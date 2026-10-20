import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  apiConfig,
  getCurrentUserEmail,
  getServerBase,
  mlUrl,
  setCurrentUser,
  setServerBase,
} from "@/lib/api";

const DEFAULT_BASE = "http://localhost:8084/ambient-invisible-intelligence";

declare global {
  interface Window {
    Capacitor?: { isNativePlatform?: () => boolean };
  }
}

describe("server base (runtime API address)", () => {
  beforeEach(() => setServerBase(null));
  afterEach(() => setServerBase(null));

  it("defaults to the built-in base URL", () => {
    expect(getServerBase()).toBe(DEFAULT_BASE);
  });

  it("stores and returns a custom server address", () => {
    setServerBase("http://192.168.1.7:8084/ambient-invisible-intelligence");
    expect(getServerBase()).toBe("http://192.168.1.7:8084/ambient-invisible-intelligence");
    expect(localStorage.getItem("spot-insight.server-base")).toBe(
      "http://192.168.1.7:8084/ambient-invisible-intelligence",
    );
  });

  it("strips trailing slashes", () => {
    setServerBase("http://192.168.1.7:8084/api///");
    expect(getServerBase()).toBe("http://192.168.1.7:8084/api");
  });

  it("falls back to the default when cleared", () => {
    setServerBase("http://192.168.1.7:8084/api");
    setServerBase(null);
    expect(getServerBase()).toBe(DEFAULT_BASE);
    expect(localStorage.getItem("spot-insight.server-base")).toBeNull();
  });

  it("apiConfig.BASE_URL reflects the runtime address", () => {
    setServerBase("http://192.168.1.7:8084/api");
    expect(apiConfig.BASE_URL).toBe("http://192.168.1.7:8084/api");
  });
});

describe("mlUrl (prediction-service address)", () => {
  beforeEach(() => setServerBase(null));
  afterEach(() => {
    setServerBase(null);
    delete window.Capacitor;
  });

  it("uses the Vite /ml proxy on the web", () => {
    expect(mlUrl("/forecast")).toBe("/ml/forecast");
  });

  it("targets the API host directly on port 5000 when native", () => {
    window.Capacitor = { isNativePlatform: () => true };
    setServerBase("http://192.168.1.7:8084/ambient-invisible-intelligence");
    expect(mlUrl("/forecast")).toBe("http://192.168.1.7:5000/forecast");
  });

  it("falls back to the proxy when the base URL is unparseable", () => {
    window.Capacitor = { isNativePlatform: () => true };
    setServerBase("not a url");
    expect(mlUrl("/forecast")).toBe("/ml/forecast");
  });
});

describe("current identity", () => {
  afterEach(() => setCurrentUser(null, null));

  it("stores the signed-in email", () => {
    setCurrentUser("driver@test.org", "Drive@1");
    expect(getCurrentUserEmail()).toBe("driver@test.org");
  });

  it("falls back to the env identity when signed out", () => {
    setCurrentUser("driver@test.org", "Drive@1");
    setCurrentUser(null, null);
    // no VITE_USER_EMAIL in the test env -> empty fallback
    expect(getCurrentUserEmail()).toBe("");
  });
});
