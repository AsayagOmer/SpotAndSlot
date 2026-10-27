from datetime import datetime, timedelta
import pytest
from app import app


@pytest.fixture
def client():
    app.config["TESTING"] = True
    with app.test_client() as c:
        yield c


def test_health_endpoint(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["status"] == "healthy"
    assert body["model_loaded"] is True
    assert "hour_of_day" in body["features"]


def test_predict_success_and_bounds(client):
    future_ts = (datetime.now() + timedelta(hours=3)).isoformat()
    resp = client.post(
        "/predict",
        json={
            "targetTimestamp": future_ts,
            "currentFreeSlots": 20,
            "totalSlots": 100,
        },
    )
    assert resp.status_code == 200
    body = resp.get_json()
    assert 0 <= body["predictedFreeSlots"] <= 100
    assert 0 <= body["predictedFreeLower"] <= body["predictedFreeSlots"]
    assert body["predictedFreeSlots"] <= body["predictedFreeUpper"] <= 100
    assert 0.0 <= body["liveBlendWeight"] <= 0.5


def test_predict_time_horizon_decay_weights_near_term_higher(client):
    near_ts = (datetime.now() + timedelta(minutes=5)).isoformat()
    far_ts = (datetime.now() + timedelta(days=3)).isoformat()

    near_resp = client.post(
        "/predict",
        json={"targetTimestamp": near_ts, "currentFreeSlots": 10, "totalSlots": 100},
    ).get_json()
    far_resp = client.post(
        "/predict",
        json={"targetTimestamp": far_ts, "currentFreeSlots": 10, "totalSlots": 100},
    ).get_json()

    assert near_resp["liveBlendWeight"] > far_resp["liveBlendWeight"]
    assert far_resp["liveBlendWeight"] < 0.01


def test_predict_rejects_invalid_inputs_with_400(client):
    # Missing targetTimestamp
    r1 = client.post("/predict", json={"totalSlots": 50})
    assert r1.status_code == 400

    # Negative totalSlots
    r2 = client.post(
        "/predict",
        json={"targetTimestamp": "2026-07-01T12:00:00Z", "totalSlots": -5},
    )
    assert r2.status_code == 400

    # Malformed timestamp
    r3 = client.post(
        "/predict",
        json={"targetTimestamp": "not-a-timestamp", "totalSlots": 50},
    )
    assert r3.status_code == 400


def test_forecast_returns_24_hourly_points(client):
    resp = client.post("/forecast", json={"date": "2026-07-01", "totalSlots": 80})
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["date"] == "2026-07-01"
    assert len(body["points"]) == 24
    for pt in body["points"]:
        assert 0 <= pt["freeLower"] <= pt["freeMedian"] <= pt["freeUpper"] <= 80


def test_forecast_rejects_invalid_date_with_400(client):
    resp = client.post("/forecast", json={"date": "invalid-date", "totalSlots": 80})
    assert resp.status_code == 400
