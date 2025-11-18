"""
Cloudflare R2 Data Upload Script
Scans local historical data and uploads to R2 bucket for Android app access
"""

import os
import sys
import json
import hashlib
import argparse
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional
import boto3
from botocore.client import Config
import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
import zstandard as zstd
from tqdm import tqdm


class R2DataUploader:
    """Uploads historical market data to Cloudflare R2"""

    def __init__(self, account_id: str, access_key: str, secret_key: str, bucket_name: str):
        self.bucket_name = bucket_name

        # R2 endpoint (S3-compatible)
        endpoint_url = f"https://{account_id}.r2.cloudflarestorage.com"

        # Initialize S3 client for R2
        self.s3_client = boto3.client(
            's3',
            endpoint_url=endpoint_url,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            config=Config(signature_version='s3v4'),
            region_name='auto'
        )

        print(f"✅ Connected to R2 bucket: {bucket_name}")

    def scan_data_directory(self, data_dir: str) -> List[Dict]:
        """Scan directory for data files"""
        data_files = []
        data_path = Path(data_dir)

        print(f"\n🔍 Scanning: {data_dir}")

        for file_path in data_path.rglob('*'):
            if file_path.is_file() and file_path.suffix in ['.csv', '.parquet']:
                file_info = self.parse_filename(file_path)
                if file_info:
                    file_info['local_path'] = str(file_path)
                    data_files.append(file_info)

        print(f"📊 Found {len(data_files)} data files")
        return data_files

    def parse_filename(self, file_path: Path) -> Optional[Dict]:
        """Parse CryptoLake/Binance filename format"""
        filename = file_path.stem
        parts = filename.split('_')

        # Format: BINANCE_BTCUSDT_20240501_20240503_ohlcv_1min
        if len(parts) >= 6 and parts[0].upper() == 'BINANCE':
            return {
                'exchange': parts[0],
                'asset': self.normalize_asset(parts[1]),
                'start_date': parts[2],
                'end_date': parts[3],
                'data_type': parts[4],
                'timeframe': self.normalize_timeframe(parts[5]),
                'format': file_path.suffix[1:],
                'filename': file_path.name
            }

        return None

    def normalize_asset(self, asset: str) -> str:
        """Normalize asset to Kraken format"""
        mapping = {
            'BTCUSDT': 'XXBTZUSD',
            'BTCUSD': 'XXBTZUSD',
            'ETHUSDT': 'XETHZUSD',
            'ETHUSD': 'XETHZUSD',
            'SOLUSDT': 'SOLUSD',
            'SOLUSD': 'SOLUSD'
        }
        return mapping.get(asset.upper(), asset)

    def normalize_timeframe(self, timeframe: str) -> str:
        """Normalize timeframe"""
        mapping = {
            '1min': '1m',
            '5min': '5m',
            '15min': '15m',
            '1hour': '1h',
            '1h': '1h',
            '4hour': '4h',
            '4h': '4h',
            '1day': '1d',
            '1d': '1d'
        }
        return mapping.get(timeframe.lower(), timeframe)

    def determine_quarter(self, date_str: str) -> str:
        """Determine quarter from date (20240501 -> 2024-Q2)"""
        year = date_str[:4]
        month = int(date_str[4:6])
        quarter = (month - 1) // 3 + 1
        return f"{year}-Q{quarter}"

    def convert_csv_to_parquet(self, csv_path: str) -> str:
        """Convert CSV to Parquet format"""
        print(f"   📝 Converting CSV to Parquet: {Path(csv_path).name}")

        # Read CSV
        df = pd.read_csv(csv_path)

        # Define schema for OHLC data
        schema = pa.schema([
            ('timestamp', pa.int64()),
            ('open', pa.float64()),
            ('high', pa.float64()),
            ('low', pa.float64()),
            ('close', pa.float64()),
            ('volume', pa.float64()),
            ('trades', pa.int32())
        ])

        # Convert to Arrow Table
        table = pa.Table.from_pandas(df, schema=schema)

        # Write Parquet (with compression)
        parquet_path = csv_path.replace('.csv', '.parquet')
        pq.write_table(
            table,
            parquet_path,
            compression='snappy',
            use_dictionary=True
        )

        return parquet_path

    def compress_file(self, file_path: str) -> str:
        """Compress file with Zstandard"""
        print(f"   🗜️  Compressing with Zstandard...")

        compressed_path = file_path + '.zst'

        with open(file_path, 'rb') as f_in:
            data = f_in.read()

        # Compress with level 3 (good balance of speed/compression)
        cctx = zstd.ZstdCompressor(level=3)
        compressed_data = cctx.compress(data)

        with open(compressed_path, 'wb') as f_out:
            f_out.write(compressed_data)

        original_size = len(data)
        compressed_size = len(compressed_data)
        ratio = original_size / compressed_size

        print(f"   ✅ Compressed: {original_size/1024/1024:.2f}MB → {compressed_size/1024/1024:.2f}MB (ratio: {ratio:.1f}x)")

        return compressed_path

    def calculate_checksum(self, file_path: str) -> str:
        """Calculate SHA256 checksum"""
        sha256 = hashlib.sha256()
        with open(file_path, 'rb') as f:
            for chunk in iter(lambda: f.read(4096), b''):
                sha256.update(chunk)
        return sha256.hexdigest()

    def generate_metadata(self, file_info: Dict, file_path: str, checksum: str) -> Dict:
        """Generate metadata JSON"""
        file_size = os.path.getsize(file_path)

        # Count bars (rows) in parquet file
        table = pq.read_table(file_path.replace('.zst', ''))
        num_bars = len(table)

        # Determine data tier
        data_tier = "TIER_4_BASIC"  # OHLCV data
        if file_info['data_type'] == 'trades':
            data_tier = "TIER_3_STANDARD"
        elif file_info['data_type'] == 'book':
            data_tier = "TIER_1_PREMIUM"

        quarter = self.determine_quarter(file_info['start_date'])

        return {
            'asset': file_info['asset'],
            'timeframe': file_info['timeframe'],
            'quarter': quarter,
            'startDate': self.date_to_timestamp(file_info['start_date']),
            'endDate': self.date_to_timestamp(file_info['end_date']),
            'bars': num_bars,
            'dataTier': data_tier,
            'source': file_info['exchange'],
            'compressed': True,
            'compressionFormat': 'zstd',
            'compressionLevel': 3,
            'sizeBytes': file_size,
            'checksumSHA256': checksum,
            'uploadedAt': datetime.now().isoformat(),
            'version': '1.0'
        }

    def date_to_timestamp(self, date_str: str) -> int:
        """Convert date string to Unix timestamp (milliseconds)"""
        dt = datetime.strptime(date_str, '%Y%m%d')
        return int(dt.timestamp() * 1000)

    def upload_to_r2(self, file_path: str, r2_key: str) -> bool:
        """Upload file to R2"""
        try:
            file_size = os.path.getsize(file_path)

            with open(file_path, 'rb') as f:
                self.s3_client.upload_fileobj(
                    f,
                    self.bucket_name,
                    r2_key,
                    Callback=lambda bytes_transferred: None
                )

            print(f"   ☁️  Uploaded: {r2_key} ({file_size/1024:.1f}KB)")
            return True
        except Exception as e:
            print(f"   ❌ Upload failed: {e}")
            return False

    def process_and_upload(self, file_info: Dict, compress: bool = True) -> bool:
        """Process single file and upload to R2"""
        local_path = file_info['local_path']
        print(f"\n📦 Processing: {file_info['filename']}")

        # Step 1: Convert CSV to Parquet if needed
        if file_info['format'] == 'csv':
            parquet_path = self.convert_csv_to_parquet(local_path)
        else:
            parquet_path = local_path

        # Step 2: Compress
        if compress:
            compressed_path = self.compress_file(parquet_path)
            upload_file = compressed_path
        else:
            upload_file = parquet_path

        # Step 3: Calculate checksum
        checksum = self.calculate_checksum(upload_file)

        # Step 4: Generate metadata
        metadata = self.generate_metadata(file_info, upload_file, checksum)

        # Step 5: Determine R2 paths
        quarter = metadata['quarter']
        asset = metadata['asset']
        timeframe = metadata['timeframe']

        data_key = f"{asset}/{timeframe}/{quarter}.parquet.zst"
        metadata_key = f"{asset}/{timeframe}/{quarter}_metadata.json"

        # Step 6: Upload data file
        if not self.upload_to_r2(upload_file, data_key):
            return False

        # Step 7: Upload metadata
        metadata_json = json.dumps(metadata, indent=2)
        self.s3_client.put_object(
            Bucket=self.bucket_name,
            Key=metadata_key,
            Body=metadata_json.encode('utf-8'),
            ContentType='application/json'
        )
        print(f"   📄 Uploaded metadata: {metadata_key}")

        # Cleanup temporary files
        if file_info['format'] == 'csv' and parquet_path != local_path:
            os.remove(parquet_path)
        if compress and compressed_path != parquet_path:
            os.remove(compressed_path)

        return True

    def generate_index(self, processed_files: List[Dict]) -> Dict:
        """Generate master index.json"""
        assets = {}

        for file_info in processed_files:
            asset = file_info['asset']
            timeframe = file_info['timeframe']
            quarter = self.determine_quarter(file_info['start_date'])

            if asset not in assets:
                assets[asset] = {}

            if timeframe not in assets[asset]:
                assets[asset][timeframe] = []

            assets[asset][timeframe].append({
                'quarter': quarter,
                'startDate': self.date_to_timestamp(file_info['start_date']),
                'endDate': self.date_to_timestamp(file_info['end_date'])
            })

        return {
            'version': '1.0',
            'generatedAt': datetime.now().isoformat(),
            'assets': assets,
            'totalAssets': len(assets),
            'totalFiles': len(processed_files)
        }

    def upload_index(self, index_data: Dict):
        """Upload index.json to R2"""
        index_json = json.dumps(index_data, indent=2)
        self.s3_client.put_object(
            Bucket=self.bucket_name,
            Key='index.json',
            Body=index_json.encode('utf-8'),
            ContentType='application/json'
        )
        print(f"\n📊 Uploaded index.json")


def main():
    parser = argparse.ArgumentParser(description='Upload historical data to Cloudflare R2')
    parser.add_argument('--source', required=True, help='Source directory with data files')
    parser.add_argument('--account-id', required=True, help='Cloudflare account ID')
    parser.add_argument('--access-key', required=True, help='R2 Access Key ID')
    parser.add_argument('--secret-key', required=True, help='R2 Secret Access Key')
    parser.add_argument('--bucket', default='crypto-trader-data', help='R2 bucket name')
    parser.add_argument('--no-compress', action='store_true', help='Skip compression')
    parser.add_argument('--dry-run', action='store_true', help='Dry run (no upload)')

    args = parser.parse_args()

    print("=" * 60)
    print("📤 Cloudflare R2 Data Upload Script")
    print("=" * 60)

    # Initialize uploader
    uploader = R2DataUploader(
        account_id=args.account_id,
        access_key=args.access_key,
        secret_key=args.secret_key,
        bucket_name=args.bucket
    )

    # Scan data directory
    data_files = uploader.scan_data_directory(args.source)

    if not data_files:
        print("❌ No data files found!")
        return 1

    print(f"\n📋 Found {len(data_files)} files to process")

    # Confirm
    if not args.dry_run:
        response = input("\n⚠️  Proceed with upload? (yes/no): ")
        if response.lower() != 'yes':
            print("❌ Upload cancelled")
            return 0

    # Process and upload each file
    processed_files = []
    for file_info in tqdm(data_files, desc="Processing files"):
        if args.dry_run:
            print(f"\n[DRY RUN] Would process: {file_info['filename']}")
            processed_files.append(file_info)
        else:
            if uploader.process_and_upload(file_info, compress=not args.no_compress):
                processed_files.append(file_info)

    # Generate and upload index
    if not args.dry_run:
        index_data = uploader.generate_index(processed_files)
        uploader.upload_index(index_data)

    print("\n" + "=" * 60)
    print(f"✅ Upload complete!")
    print(f"   Files processed: {len(processed_files)}/{len(data_files)}")
    print("=" * 60)

    return 0


if __name__ == '__main__':
    sys.exit(main())
