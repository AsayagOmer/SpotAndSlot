import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "@/lib/auth";
import RequireAuth from "@/components/RequireAuth";
import NotFound from "@/pages/NotFound";
import AdminLayout from "./components/AdminLayout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Lots from "./pages/Lots";
import Users from "./pages/Users";
import Activity from "./pages/Activity";

const queryClient = new QueryClient();

// Desktop staff console with role-based sections:
// - OPERATOR: Dashboard + My Lots (build and manage the parking objects they own)
// - ADMIN: Users + Activity (the Admin API: manage users, view command history)
// End users use the mobile app (src/apps/mobile).
const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <AuthProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            {/* console shell: any staff role (ADMIN or OPERATOR) */}
            <Route
              element={
                <RequireAuth roles={["ADMIN", "OPERATOR"]} fallbackTo="/login">
                  <AdminLayout />
                </RequireAuth>
              }
            >
              {/* OPERATOR area — reads/writes parking objects */}
              <Route path="/" element={<RequireAuth role="OPERATOR" fallbackTo="/users"><Dashboard /></RequireAuth>} />
              <Route path="/lots" element={<RequireAuth role="OPERATOR" fallbackTo="/users"><Lots /></RequireAuth>} />
              {/* ADMIN area — the Admin API */}
              <Route path="/users" element={<RequireAuth role="ADMIN" fallbackTo="/"><Users /></RequireAuth>} />
              <Route path="/activity" element={<RequireAuth role="ADMIN" fallbackTo="/"><Activity /></RequireAuth>} />
            </Route>
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
