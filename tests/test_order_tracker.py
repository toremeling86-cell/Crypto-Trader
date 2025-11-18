from crypto_trader.orders.order_tracker import Order, OrderStatus, OrderTracker


def test_order_tracker_submit_and_get():
    tracker = OrderTracker()
    order = Order(order_id="1", symbol="BTCUSD", side="buy", quantity=1.0, price=100.0)
    tracker.submit(order)
    retrieved = tracker.get_order("1")
    assert retrieved.symbol == "BTCUSD"
    assert retrieved.status == OrderStatus.PENDING


def test_order_tracker_update_status():
    tracker = OrderTracker()
    order = Order(order_id="2", symbol="ETHUSD", side="sell", quantity=2.0, price=50.0)
    tracker.submit(order)
    tracker.update_status("2", OrderStatus.FILLED)
    assert tracker.get_order("2").status == OrderStatus.FILLED
