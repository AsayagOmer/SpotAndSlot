import { useState } from "react";
import { Loader2, Trash2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogTrigger,
} from "@/components/ui/dialog";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

export const roleStyle = (role: string) =>
  role === "ADMIN"
    ? "bg-primary/15 text-primary border-primary/30"
    : role === "OPERATOR"
    ? "bg-blue-500/15 text-blue-600 border-blue-500/30"
    : "bg-muted text-muted-foreground border-border";

export const StatCard = ({
  icon: Icon, label, value, sub, tone = "default",
}: {
  icon: React.ElementType; label: string; value: React.ReactNode; sub?: string;
  tone?: "default" | "primary" | "danger" | "ok";
}) => {
  const toneCls = {
    default: "text-foreground", primary: "text-primary",
    danger: "text-destructive", ok: "text-emerald-600",
  }[tone];
  return (
    <Card className="p-4 flex items-center gap-3">
      <div className={`w-10 h-10 rounded-xl bg-muted flex items-center justify-center shrink-0 ${toneCls}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <div className={`text-2xl font-bold tabular-nums leading-none ${toneCls}`}>{value}</div>
        <div className="text-xs text-muted-foreground mt-1 truncate">{label}{sub ? ` · ${sub}` : ""}</div>
      </div>
    </Card>
  );
};

export const Metric = ({ label, value, tone = "default" }: { label: string; value: number; tone?: "default" | "ok" | "danger" }) => {
  const c = tone === "ok" ? "text-emerald-600" : tone === "danger" ? "text-destructive" : "text-foreground";
  return (
    <div className="rounded-xl bg-muted/50 p-3 text-center">
      <div className={`text-xl font-bold tabular-nums ${c}`}>{value}</div>
      <div className="text-[11px] text-muted-foreground mt-0.5">{label}</div>
    </div>
  );
};

// Small reusable rename dialog
export const RenameDialog = ({
  title, current, onSave, children,
}: {
  title: string; current: string; onSave: (name: string) => Promise<void>; children: React.ReactNode;
}) => {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(current);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (o) setName(current); }}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <Input value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        <DialogFooter>
          <Button
            disabled={busy || !name.trim()}
            onClick={async () => {
              setBusy(true);
              try { await onSave(name.trim()); setOpen(false); }
              finally { setBusy(false); }
            }}
          >
            {busy && <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />} Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

// Confirm-and-delete button (lot / section / slot)
export const DeleteButton = ({
  what, onDelete, size = "sm",
}: { what: string; onDelete: () => Promise<void>; size?: "sm" | "icon-xs" }) => {
  const [busy, setBusy] = useState(false);
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        {size === "sm" ? (
          <Button size="sm" variant="outline" className="text-destructive border-destructive/40 hover:bg-destructive/10">
            <Trash2 className="w-4 h-4 mr-1.5" /> Delete
          </Button>
        ) : (
          <button className="w-6 h-6 rounded flex items-center justify-center text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete {what}?</AlertDialogTitle>
          <AlertDialogDescription>
            This permanently removes {what} (children are removed too). This cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            disabled={busy}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            onClick={async (e) => {
              e.preventDefault();
              setBusy(true);
              try { await onDelete(); } finally { setBusy(false); }
            }}
          >
            {busy && <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />} Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
};
