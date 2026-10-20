import { MapPin } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSelectedLot } from "@/lib/selectedLot";

// Lets the user choose which parking lot to view; hidden when only one exists.
const LotSelector = () => {
  const { lots, selectedLotId, setSelectedLotId } = useSelectedLot();

  if (lots.length <= 1) return null;

  return (
    <Select value={selectedLotId ?? undefined} onValueChange={setSelectedLotId} dir="rtl">
      <SelectTrigger className="w-full bg-card rounded-2xl h-12 border-border hover:border-primary/30 transition-colors">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <MapPin className="w-4 h-4 text-primary shrink-0" />
          <span className="min-w-0 flex-1 text-right" dir="rtl">
          <SelectValue placeholder="בחר חניון" />
          </span>
        </div>
      </SelectTrigger>
      <SelectContent>
        {lots.map((lot) => {
          const id = lot.id?.objectId ?? "";
          const city = (lot.objectDetails as { address?: string } | undefined)?.address;
          return (
            <SelectItem key={id} value={id} className="justify-end pl-2 pr-8 text-right">
              <span className="block w-full text-right" dir="rtl">
                {lot.alias ?? "חניון"}
                {city ? ` · ${city}` : ""}
              </span>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
};

export default LotSelector;
