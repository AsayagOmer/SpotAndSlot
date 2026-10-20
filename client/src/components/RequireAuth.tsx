import type { ReactElement } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/lib/auth";

// Route guard: unauthenticated users are sent to /login; users whose role is
// not among `roles` are sent to `fallbackTo` (the admin app points this at
// /login so a rejected session can never loop through its protected routes).
const RequireAuth = ({
  role,
  roles,
  fallbackTo = "/",
  children,
}: {
  role?: string;
  roles?: string[];
  fallbackTo?: string;
  children: ReactElement;
}) => {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  const allowed = roles ?? (role ? [role] : null);
  if (allowed && !allowed.includes(user.role)) return <Navigate to={fallbackTo} replace />;
  return children;
};

export default RequireAuth;
