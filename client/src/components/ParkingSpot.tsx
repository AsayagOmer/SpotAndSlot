import { Accessibility, X } from "lucide-react";
import { cn } from "@/lib/utils";

interface ParkingSpotProps {
  status: "available" | "occupied" | "disabled-available" | "disabled-occupied" | "saved";
  id: string;
  onClick?: () => void;
  size?: "sm" | "md" | "lg";
}

const ParkingSpot = ({ status, id, onClick, size = "md" }: ParkingSpotProps) => {
  const sizeClasses = {
    sm: "w-6 h-8",
    md: "w-8 h-10",
    lg: "w-10 h-12"
  };

  const isDisabled = status === "disabled-available" || status === "disabled-occupied";
  const isOccupied = status === "occupied" || status === "disabled-occupied";
  const isSaved = status === "saved";

  return (
    <button
      onClick={onClick}
      className={cn(
        "rounded-sm flex items-center justify-center transition-all duration-300 border border-foreground/20 shadow-sm",
        sizeClasses[size],
        status === "available" && "bg-primary hover:bg-primary/90 text-primary-foreground",
        status === "occupied" && "bg-gray-500 text-white border-gray-600",
        status === "disabled-available" && "bg-primary hover:bg-primary/90 text-primary-foreground",
        status === "disabled-occupied" && "bg-gray-500 text-white border-gray-600",
        status === "saved" && "bg-accent border-2 border-primary",
        !isOccupied && "hover:scale-105 active:scale-95"
      )}
    >
      {status === "occupied" && (
        <X className="w-4 h-4" />
      )}
      {isDisabled && (
        <Accessibility
          className={cn(
            "w-4 h-4",
            isOccupied || status === "disabled-available" ? "text-primary-foreground" : "text-foreground"
          )}
        />
      )}
      {isSaved && (
        <span className="text-xs font-bold text-primary">P</span>
      )}
    </button>
  );
};

export default ParkingSpot;
