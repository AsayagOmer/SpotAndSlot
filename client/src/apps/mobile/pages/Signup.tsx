import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { UserPlus, Loader2, ParkingSquare } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";

// Public self sign-up. New accounts are always END_USER; admins and operators
// are created from the admin console by an existing administrator.
const AVATARS = ["🙂", "😎", "🚗", "🅿️", "🦸", "🌟"];

const Signup = () => {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [avatar, setAvatar] = useState(AVATARS[0]);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      toast.error("הסיסמאות אינן תואמות", { position: "top-center" });
      return;
    }
    // the server requires a non-blank username and avatar
    if (!username.trim()) {
      toast.error("יש להזין שם מלא", { position: "top-center" });
      return;
    }
    setBusy(true);
    try {
      await signup({ email: email.trim(), password, username: username.trim(), avatar });
      toast.success("החשבון נוצר — ברוך הבא!", { position: "top-center" });
      navigate("/", { replace: true });
    } catch (err) {
      // A network / mixed-content failure rejects with a plain TypeError, not an
      // ApiError — surface that as "can't reach server" rather than a data error.
      if (!(err instanceof ApiError)) {
        toast.error("לא ניתן להתחבר לשרת", {
          description: "בדוק את כתובת השרת ואת החיבור לרשת",
          position: "top-center",
        });
      } else {
        const msg = err.message;
        toast.error(
          msg.includes("password")
            ? "סיסמה חלשה — נדרשים לפחות 5 תווים, ספרה ותו מיוחד"
            : msg.includes("exists")
            ? "משתמש עם אימייל זה כבר קיים"
            : "ההרשמה נכשלה — נסה שוב",
          { position: "top-center" },
        );
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
          <h1 className="text-2xl font-semibold tracking-tight">יצירת חשבון</h1>
          <p className="text-sm text-muted-foreground">
            הרשמה מהירה — ותוכל לבחור חניון ולעקוב אחרי מקומות פנויים
          </p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="card-elevated p-6 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">שם מלא</Label>
            <Input
              id="username"
              autoComplete="name"
              placeholder="ישראל ישראלי"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <Label>אווטאר</Label>
            <div className="flex gap-2 flex-wrap">
              {AVATARS.map((a) => (
                <button
                  key={a}
                  type="button"
                  onClick={() => setAvatar(a)}
                  className={`w-10 h-10 rounded-xl text-xl flex items-center justify-center border transition-colors ${
                    avatar === a
                      ? "border-primary bg-primary/10"
                      : "border-border bg-card hover:border-primary/40"
                  }`}
                >
                  {a}
                </button>
              ))}
            </div>
          </div>
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
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <p className="text-xs text-muted-foreground">לפחות 5 תווים, כולל ספרה ותו מיוחד (למשל User@1)</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="confirm">אימות סיסמה</Label>
            <Input
              id="confirm"
              type="password"
              dir="ltr"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </div>
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? <Loader2 className="w-4 h-4 ml-2 animate-spin" /> : <UserPlus className="w-4 h-4 ml-2" />}
            הירשם
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          כבר יש לך חשבון?{" "}
          <Link to="/login" className="text-primary font-medium hover:underline">
            התחבר
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Signup;
