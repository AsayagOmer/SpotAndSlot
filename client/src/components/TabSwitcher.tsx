import { Radio, Sparkles } from "lucide-react";

import { cn } from "@/lib/utils";

interface TabSwitcherProps {
  activeTab: "live" | "predictions";
  onTabChange: (tab: "live" | "predictions") => void;
}

const TabSwitcher = ({ activeTab, onTabChange }: TabSwitcherProps) => {
  return (
    <div className="bg-muted border border-transparent shadow-inner rounded-2xl p-1.5 flex gap-1">
      <button
        onClick={() => onTabChange("live")}
        className={cn(
          "flex-1 py-3 px-6 rounded-xl text-base transition-all duration-300 inline-flex items-center justify-center gap-2",
          activeTab === "live"
            ? "bg-primary/10 text-primary border border-primary/20 font-bold shadow-sm"
            : "text-foreground hover:bg-muted font-medium"
        )}
      >
        <Radio className={cn("h-4 w-4", activeTab === "live" && "fill-current")} aria-hidden="true" />
        זמן אמת
      </button>
      <button
        onClick={() => onTabChange("predictions")}
        className={cn(
          "flex-1 py-3 px-6 rounded-xl text-base transition-all duration-300 inline-flex items-center justify-center gap-2",
          activeTab === "predictions"
            ? "bg-primary/10 text-primary border border-primary/20 font-bold shadow-sm"
            : "text-foreground hover:bg-muted font-medium"
        )}
      >
        <Sparkles className={cn("h-4 w-4", activeTab === "predictions" && "fill-current")} aria-hidden="true" />
        תחזית
      </button>
    </div>
  );
};

export default TabSwitcher;
