import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Shield, LogIn, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/lib/auth";

// Desktop staff sign-in. ADMIN and OPERATOR accounts may enter the console
// (admins manage users/activity, operators manage lots). Drivers (END_USER)
// belong in the mobile app.
const Login = () => {
  const { login, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) return;
    setBusy(true);
    try {
      const user = await login(email.trim(), password);
      if (user.role !== "ADMIN" && user.role !== "OPERATOR") {
        logout();
        toast.error("Staff account required", {
          description: "This console is for administrators and operators. Drivers should use the Spot&Slot mobile app.",
        });
        return;
      }
      toast.success(`Welcome back, ${user.username || user.email}`);
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
      // admins land on Users, operators on the lots dashboard
      const home = user.role === "ADMIN" ? "/users" : "/";
      navigate(from ?? home, { replace: true });
    } catch {
      toast.error("Sign-in failed — check your email and password");
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
            <Shield className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">
            <span className="text-secondary">Spot</span>
            <span className="text-primary">&</span>
            <span className="text-secondary">Slot</span>
            <span className="text-muted-foreground font-normal"> · Admin</span>
          </h1>
          <p className="text-sm text-muted-foreground">Sign in to manage your parking lots</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="card-elevated p-6 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="admin@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <LogIn className="w-4 h-4 mr-2" />}
            Sign in
          </Button>
        </form>

        <p className="text-center text-xs text-muted-foreground">
          Administrator accounts are created by an existing admin from the Users page.
        </p>
      </div>
    </div>
  );
};

export default Login;
