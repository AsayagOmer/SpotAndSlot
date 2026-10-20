import { TrendingUp } from "lucide-react";

interface SmartInsightsProps {
  trendDelta: number;
}

const SmartInsights = ({ trendDelta }: SmartInsightsProps) => {
  const directionText =
    trendDelta > 0
      ? `צפויה עלייה של ${trendDelta}% עד הצהריים`
      : trendDelta < 0
        ? `צפויה ירידה של ${Math.abs(trendDelta)}% עד הצהריים`
        : "לא צפוי שינוי מהותי עד הצהריים";

  return (
    <div className="space-y-4 animate-fade-in">
      <h3 className="font-semibold text-sm text-muted-foreground mb-2">מגמת תפוסה</h3>

      <div className="card-elevated p-4 flex items-center gap-4">
        <div className="w-12 h-12 rounded-2xl bg-accent flex items-center justify-center">
          <TrendingUp className="w-6 h-6 text-primary" />
        </div>
        <div className="flex-1 text-right">
          <p className="text-sm text-muted-foreground">מגמת תפוסה</p>
          <p className="font-semibold">{directionText}</p>
        </div>
      </div>
    </div>
  );
};

export default SmartInsights;
