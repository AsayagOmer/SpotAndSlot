import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { LogIn, Loader2, ParkingSquare, Settings2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/lib/auth";
import { getServerBase, setServerBase, ApiError } from "@/lib/api";

const Login = () => {
  const { login, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  // server address (needed when the app runs on a phone: "localhost" is the phone)
  const [showServer, setShowServer] = useState(false);
  const [server, setServer] = useState(getServerBase());

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) return;
    setServerBase(server);
    setBusy(true);
    try {
      const user = await login(email.trim(), password);
      // this app is for drivers (END_USER); staff use the desktop console
      if (user.role !== "END_USER") {
        logout();
        toast.error("האפליקציה מיועדת לנהגים", {
          description: "מנהלים ומפעילים משתמשים בקונסולת הניהול בדפדפן",
          position: "top-center",
        });
        return;
      }
      toast.success(`ברוך הבא, ${user.username || user.email}!`, { position: "top-center" });
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
      navigate(from ?? "/", { replace: true });
    } catch (err) {
      // Tell a real credential rejection (HTTP 401) apart from the app simply
      // not being able to reach the server (network / mixed-content error,
      // which rejects with a TypeError rather than an ApiError).
      if (err instanceof ApiError && err.status === 401) {
        toast.error("אימייל או סיסמה שגויים", { position: "top-center" });
      } else if (err instanceof ApiError) {
        toast.error(`שגיאת שרת (${err.status})`, { position: "top-center" });
      } else {
        toast.error("לא ניתן להתחבר לשרת", {
          description: "בדוק את כתובת השרת ואת החיבור לרשת",
          position: "top-center",
        });
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-4">
      <div className="w-full max-w-sm space-y-6 animate-fade-in">
        {/* Brand */}
        <div className="text-center space-y-2">
          <div className="mx-auto w-14 h-14 rounded-2xl bg-primary/15 text-primary flex items-center justify-center">
            <ParkingSquare className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">
            <span className="text-secondary">Spot</span>
            <span className="text-primary">&</span>
            <span className="text-secondary">Slot</span>
          </h1>
          <p className="text-sm text-muted-foreground">התחבר כדי לצפות בחניונים בזמן אמת</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="card-elevated p-6 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">אימייל</Label>
            <Input
              id="email"
              type="email"
              dir="ltr"
              autoComplete="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">סיסמה</Label>
            <Input
              id="password"
              type="password"
              dir="ltr"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <LogIn className="w-4 h-4 ml-2" />}
            התחבר
          </Button>

          {/* server address (for running the app on a device) */}
          <div className="pt-1">
            <button
              type="button"
              onClick={() => setShowServer((s) => !s)}
              className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-primary transition-colors"
            >
              <Settings2 className="w-3.5 h-3.5" />
              כתובת שרת
            </button>
            {showServer && (
              <div className="mt-2 space-y-1">
                <Input
                  dir="ltr"
                  className="text-xs h-9"
                  placeholder="http://192.168.1.20:8084/ambient-invisible-intelligence"
                  value={server}
                  onChange={(e) => setServer(e.target.value)}
                  onBlur={() => setServerBase(server)}
                />
                <p className="text-[11px] text-muted-foreground">
                  במכשיר אמיתי: כתובת ה-IP של המחשב שמריץ את השרת
                </p>
              </div>
            )}
          </div>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          אין לך חשבון?{" "}
          <Link to="/signup" className="text-primary font-medium hover:underline">
            הירשם עכשיו
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
