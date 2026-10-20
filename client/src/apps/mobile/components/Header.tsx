import { LogOut } from "lucide-react";
import { useAuth } from "@/lib/auth";

// Mobile app header: brand + signed-in user. Administration is handled by the
// separate desktop admin app, so there is no admin entry here.
const Header = () => {
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-50 bg-card/80 backdrop-blur-lg border-b border-border px-4 py-3">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center">
        {/* signed-in user + logout */}
        <div className="flex items-center gap-1.5 justify-self-start min-w-0">
          {user && (
            <>
              <button
                onClick={logout}
                aria-label="התנתק"
                title="התנתק"
                className="w-9 h-9 rounded-xl flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-muted transition-colors shrink-0"
              >
                <LogOut className="w-5 h-5" />
              </button>
              <span className="text-xs text-muted-foreground truncate hidden sm:block">
                {user.username || user.email}
              </span>
            </>
          )}
        </div>

        <h1 className="text-xl font-semibold tracking-tight justify-self-center">
          <span className="text-secondary">Spot</span>
          <span className="text-primary">&</span>
          <span className="text-secondary">Slot</span>
        </h1>

        <div />
      </div>
    </header>
  );
};

export default Header;
