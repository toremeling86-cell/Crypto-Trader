import pytest

pd = pytest.importorskip("pandas")

from crypto_trader.data.parquet_loader import ParquetDataSet


def test_parquet_dataset_load_and_iter(tmp_path):
    data = pd.DataFrame({"symbol": ["BTC"], "price": [30000.0]})
    file_path = tmp_path / "prices.parquet"
    data.to_parquet(file_path)

    dataset = ParquetDataSet(path=file_path)
    loaded = dataset.load()
    assert loaded.equals(data)

    batches = list(dataset.iter_batches(batch_size=1))
    assert len(batches) == 1
    assert batches[0].iloc[0]["symbol"] == "BTC"
