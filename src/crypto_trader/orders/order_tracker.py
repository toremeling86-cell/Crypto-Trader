"""Order tracking scaffolding for future trading engine integration."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Dict, List


class OrderStatus(str, Enum):
    PENDING = "pending"
    FILLED = "filled"
    CANCELLED = "cancelled"


@dataclass
class Order:
    order_id: str
    symbol: str
    side: str
    quantity: float
    price: float
    status: OrderStatus = OrderStatus.PENDING
    created_at: datetime = field(default_factory=datetime.utcnow)


class OrderTracker:
    """In-memory order tracking store suitable for unit testing."""

    def __init__(self) -> None:
        self._orders: Dict[str, Order] = {}

    def submit(self, order: Order) -> None:
        if order.order_id in self._orders:
            raise ValueError(f"Order {order.order_id} already exists")
        self._orders[order.order_id] = order

    def update_status(self, order_id: str, status: OrderStatus) -> None:
        if order_id not in self._orders:
            raise KeyError(f"Order {order_id} not found")
        self._orders[order_id].status = status

    def list_orders(self) -> List[Order]:
        return list(self._orders.values())

    def get_order(self, order_id: str) -> Order:
        if order_id not in self._orders:
            raise KeyError(f"Order {order_id} not found")
        return self._orders[order_id]


__all__ = ["Order", "OrderTracker", "OrderStatus"]
