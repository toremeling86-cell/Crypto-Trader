# Cloudflare R2 Setup Guide

Complete guide for setting up Cloudflare R2 and uploading historical market data.

---

## Step 1: Create Cloudflare Account & Enable R2

### 1.1 Sign Up for Cloudflare
1. Go to https://dash.cloudflare.com/sign-up
2. Create account with email + password
3. Verify email address

### 1.2 Enable R2 Storage
1. Login to Cloudflare Dashboard
2. Click "R2" in left sidebar
3. Click "Purchase R2 Plan"
4. **Choose Plan:**
   - Workers Free (includes 10GB R2 free)
   - No credit card required for free tier
   - Only pay if you exceed 10GB ($0.015/GB/month)
5. Click "Enable R2"

---

## Step 2: Create R2 Bucket

### 2.1 Create Bucket
1. In R2 dashboard, click "Create bucket"
2. **Bucket name:** `crypto-trader-data` (or your choice)
3. **Location:** Automatic (optimal performance)
4. Click "Create bucket"

### 2.2 Configure Bucket (Optional)
- **Public Access:** Leave disabled (private by default)
- **CORS:** Not needed for now
- **Lifecycle Rules:** Can add later for auto-cleanup

---

## Step 3: Generate API Tokens

### 3.1 Create R2 API Token
1. In R2 dashboard, click "Manage R2 API Tokens"
2. Click "Create API token"
3. **Token Name:** `crypto-trader-upload`
4. **Permissions:**
   - ✅ Object Read & Write
   - ✅ Bucket Read
5. **TTL:** Never expires (or set custom)
6. Click "Create API token"

### 3.2 Save Credentials
**IMPORTANT:** Save these immediately (shown only once):

```
Account ID: abc123def456... (Cloudflare account ID)
Access Key ID: 1234567890abcdef...
Secret Access Key: abcdefghijklmnop...
```

**Store securely** - you'll need these for the upload script!

---

## Step 4: Install Python Dependencies

### 4.1 Ensure Python is Installed
```bash
python --version
# Should be Python 3.8+
```

### 4.2 Install Required Packages
```bash
cd D:\Development\Projects\Mobile\Android\CryptoTrader\scripts
pip install -r requirements.txt
```

**Packages installed:**
- `boto3` - AWS S3 client (R2 compatible)
- `pandas` - Data manipulation
- `pyarrow` - Parquet file support
- `zstandard` - Compression
- `tqdm` - Progress bars

---

## Step 5: Upload Data to R2

### 5.1 Dry Run (Test First)
Test without actually uploading:

```bash
python upload_to_r2.py \
  --source "D:\Development\Projects\Mobile\Android\CryptoTrader\data" \
  --account-id YOUR_ACCOUNT_ID \
  --access-key YOUR_ACCESS_KEY \
  --secret-key YOUR_SECRET_KEY \
  --bucket crypto-trader-data \
  --dry-run
```

This will show what would be processed without uploading anything.

### 5.2 Real Upload
When ready, remove `--dry-run`:

```bash
python upload_to_r2.py \
  --source "D:\Development\Projects\Mobile\Android\CryptoTrader\data" \
  --account-id YOUR_ACCOUNT_ID \
  --access-key YOUR_ACCESS_KEY \
  --secret-key YOUR_SECRET_KEY \
  --bucket crypto-trader-data
```

### 5.3 What Happens During Upload

The script will:
1. **Scan** - Find all `.csv` and `.parquet` files
2. **Parse** - Extract asset, timeframe, dates from filename
3. **Convert** - Transform CSV to Parquet (if needed)
4. **Compress** - Apply Zstandard compression (~12x ratio)
5. **Upload** - Push to R2 with metadata
6. **Index** - Generate master `index.json`

**Example output:**
```
============================================================
📤 Cloudflare R2 Data Upload Script
============================================================
✅ Connected to R2 bucket: crypto-trader-data

🔍 Scanning: D:\Development\...\data
📊 Found 132 data files

📋 Found 132 files to process

⚠️  Proceed with upload? (yes/no): yes

📦 Processing: BINANCE_BTCUSDT_20240501_20240502_ohlcv_1h.csv
   📝 Converting CSV to Parquet: BINANCE_BTCUSDT_20240501_20240502_ohlcv_1h.csv
   🗜️  Compressing with Zstandard...
   ✅ Compressed: 2.5MB → 0.15MB (ratio: 16.7x)
   ☁️  Uploaded: XXBTZUSD/1h/2024-Q2.parquet.zst (150KB)
   📄 Uploaded metadata: XXBTZUSD/1h/2024-Q2_metadata.json

...

📊 Uploaded index.json

============================================================
✅ Upload complete!
   Files processed: 132/132
============================================================
```

---

## Step 6: Verify Upload

### 6.1 Check R2 Dashboard
1. Go to Cloudflare Dashboard → R2
2. Click on your bucket: `crypto-trader-data`
3. You should see folders: `XXBTZUSD/`, `XETHZUSD/`, etc.

### 6.2 Check File Structure
```
crypto-trader-data/
├── index.json
├── XXBTZUSD/
│   ├── manifest.json
│   ├── 1h/
│   │   ├── 2024-Q1.parquet.zst
│   │   ├── 2024-Q1_metadata.json
│   │   ├── 2024-Q2.parquet.zst
│   │   └── 2024-Q2_metadata.json
│   └── 4h/
└── XETHZUSD/
```

### 6.3 Check Storage Usage
- Click "Metrics" in R2 dashboard
- Verify storage used (should be ~1-5MB if 132 files)
- Check operation counts (uploads, reads)

---

## Step 7: Get R2 Public URL (for Android App)

### 7.1 Option A: Custom Domain (Recommended)
1. In bucket settings, click "Custom Domains"
2. Add domain: `data.yourapp.com`
3. Follow DNS configuration steps
4. **Result:** `https://data.yourapp.com/XXBTZUSD/1h/2024-Q2.parquet.zst`

### 7.2 Option B: R2.dev Subdomain (Easier)
1. In bucket settings, enable "Public Access" (only if needed)
2. Click "Allow Access"
3. **Result:** `https://pub-xyz123.r2.dev/XXBTZUSD/1h/2024-Q2.parquet.zst`

### 7.3 Option C: S3-compatible URL (Private, for API)
**Endpoint:** `https://<account-id>.r2.cloudflarestorage.com`
**Access:** Requires API keys (most secure)

---

## Cost Estimate

Based on your data (132 files, ~30GB):

### Free Tier (First 10GB)
- Storage: $0
- Operations: $0 (10M reads/month free)
- Egress: $0 (R2 has NO egress fees!)

### Beyond Free Tier (20GB extra)
- Storage: 20GB × $0.015 = **$0.30/month**
- Operations: Included (10M free)
- Egress: **$0** (always free!)

**Total Cost: ~$0.30/month (~3 kr/month)** 🎉

### If You Scale to 100GB:
- Storage: 90GB × $0.015 = **$1.35/month** (~14 kr)
- Total: Still cheaper than competitors!

---

## Troubleshooting

### Error: "NoSuchBucket"
- Bucket name might be wrong
- Check bucket exists in R2 dashboard

### Error: "AccessDenied"
- Check API token permissions (Object Read & Write)
- Verify Access Key ID and Secret are correct
- Regenerate token if needed

### Error: "ModuleNotFoundError: No module named 'boto3'"
- Run: `pip install -r requirements.txt`
- Use correct Python environment

### Slow Upload
- Normal! Compression + upload takes time
- 132 files = ~15-30 minutes depending on connection
- Consider running overnight for large datasets

### Script Crashes Mid-Upload
- Re-run script - it will skip already uploaded files
- Check disk space for temporary files
- Check internet connection stability

---

## Next Steps

Once upload is complete:

1. ✅ Note your R2 bucket URL
2. ✅ Save account credentials securely
3. ✅ Proceed to **Fase 2**: Android app implementation
4. ✅ Android app will download data on-demand from R2

---

## Security Best Practices

### API Keys
- ✅ Never commit credentials to Git
- ✅ Use environment variables
- ✅ Regenerate tokens periodically
- ✅ Use separate tokens for upload vs download

### Bucket Access
- ✅ Keep bucket private unless needed
- ✅ Use presigned URLs for temporary access
- ✅ Enable CORS only if using web app
- ✅ Monitor access logs

---

## Maintenance

### Adding New Data
Just run the script again:
```bash
python upload_to_r2.py \
  --source "D:\...\data" \
  --account-id ... \
  --access-key ... \
  --secret-key ...
```

It will:
- Skip already uploaded files (by checking metadata)
- Upload only new files
- Update index.json

### Updating Existing Data
- Delete old file in R2 dashboard
- Re-run upload script
- Or use `--force-upload` flag (future enhancement)

---

## Questions?

Check your upload output logs for errors.
Most issues are related to API credentials or network connectivity.
