import { NavLink, Outlet, useLocation } from "react-router-dom";
import {
  Shield, LayoutDashboard, LayoutGrid, Users as UsersIcon, Activity, LogOut, RefreshCw,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import {
  SidebarProvider, Sidebar, SidebarHeader, SidebarContent, SidebarFooter,
  SidebarGroup, SidebarGroupLabel, SidebarGroupContent, SidebarMenu,
  SidebarMenuItem, SidebarMenuButton, SidebarInset, SidebarTrigger, SidebarSeparator,
} from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { apiConfig } from "@/lib/api";
import { useAuth } from "@/lib/auth";

// Nav is role-based: OPERATORs manage parking objects; ADMINs use the Admin API.
const OPERATOR_NAV = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/lots", label: "My Lots", icon: LayoutGrid, end: false },
];
const ADMIN_NAV = [
  { to: "/users", label: "Users", icon: UsersIcon, end: false },
  { to: "/activity", label: "Activity", icon: Activity, end: false },
];

const PAGE_TITLES: Record<string, string> = {
  "/": "Dashboard",
  "/lots": "My Lots",
  "/users": "Users",
  "/activity": "Activity",
};

// Desktop staff console shell: fixed sidebar navigation + wide content area.
const AdminLayout = () => {
  const { user, logout, isAdmin } = useAuth();
  const location = useLocation();
  const queryClient = useQueryClient();
  const nav = isAdmin ? ADMIN_NAV : OPERATOR_NAV;

  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader className="px-4 py-4">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-primary/15 text-primary flex items-center justify-center shrink-0">
              <Shield className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <div className="font-semibold leading-tight truncate">
                <span className="text-secondary">Spot</span>
                <span className="text-primary">&</span>
                <span className="text-secondary">Slot</span>
              </div>
              <div className="text-[11px] text-muted-foreground truncate">
                {isAdmin ? "Admin Console" : "Operator Console"}
              </div>
            </div>
          </div>
        </SidebarHeader>
        <SidebarSeparator />
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>{isAdmin ? "Administration" : "Management"}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {nav.map((item) => (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton
                      asChild
                      isActive={item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)}
                    >
                      <NavLink to={item.to} end={item.end}>
                        <item.icon />
                        <span>{item.label}</span>
                      </NavLink>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarSeparator />
        <SidebarFooter className="px-3 py-3">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-muted flex items-center justify-center text-sm shrink-0">
              {user?.avatar || "🛡️"}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium truncate">{user?.username || user?.email}</div>
              <div className="text-[11px] text-muted-foreground truncate">{user?.email}</div>
            </div>
            <button
              onClick={logout}
              title="Sign out"
              className="w-8 h-8 rounded-lg flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-muted transition-colors shrink-0"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </SidebarFooter>
      </Sidebar>

      <SidebarInset>
        {/* top bar */}
        <header className="sticky top-0 z-40 bg-card/80 backdrop-blur-lg border-b border-border">
          <div className="flex items-center justify-between gap-3 px-4 py-2.5">
            <div className="flex items-center gap-2 min-w-0">
              <SidebarTrigger />
              <h1 className="font-semibold truncate">{PAGE_TITLES[location.pathname] ?? "Admin"}</h1>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <span className="hidden md:flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                {user?.role} · {apiConfig.SYSTEM_ID}
              </span>
              <Button variant="outline" size="sm" onClick={() => queryClient.invalidateQueries()}>
                <RefreshCw className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </header>

        <main className="p-6">
          <div className="max-w-screen-2xl mx-auto space-y-6">
            <Outlet />
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
};

export default AdminLayout;
