import { useState, useEffect, useRef } from "react";
import { Bell } from "lucide-react";
import { toast } from "sonner";
import Header from "../components/Header";
import TabSwitcher from "@/components/TabSwitcher";
import ParkingLotMap from "@/components/ParkingLotMap";
import PredictionsView from "@/components/PredictionsView";
import LotSelector from "@/components/LotSelector";
import { useParkingData } from "@/hooks/useParkingData";

const Index = () => {
  const [activeViewTab, setActiveViewTab] = useState<"live" | "predictions">("live");
  const [isWaitingForSpot, setIsWaitingForSpot] = useState(false);
  const prevFreeCount = useRef<number | null>(null);

  const { data } = useParkingData(5000);

  useEffect(() => {
    if (data?.freeCount !== undefined) {
      if (
        isWaitingForSpot &&
        prevFreeCount.current !== null &&
        data.freeCount > prevFreeCount.current
      ) {
        toast.success("מקום חניה התפנה בחניון!", {
          description: `יש כעת ${data.freeCount} חניות פנויות.`,
          duration: 10000,
          position: "top-center",
        });
        setIsWaitingForSpot(false);
      }
      prevFreeCount.current = data.freeCount;
    }
  }, [data?.freeCount, isWaitingForSpot]);

  const handleNotifyClick = () => {
    if (isWaitingForSpot) {
      setIsWaitingForSpot(false);
      toast.info("ביטלת את ההתראה", { position: "top-center" });
    } else {
      if (data && data.freeCount > 0) {
        toast.info("יש כבר חניות פנויות בחניון!", { position: "top-center" });
      } else {
        setIsWaitingForSpot(true);
        toast.success("נרשמת לקבלת התראה", {
          description: "נודיע לך ברגע שיתפנה מקום",
          position: "top-center",
        });
      }
    }
  };

  return (
    <div className="min-h-screen bg-background pb-6">
      <Header />

      <main className="container max-w-md mx-auto px-4 pb-5 space-y-5">
        {/* Sticky Tab Switcher Container */}
        <div className="sticky top-0 z-50 py-4 -mx-4 px-4 bg-background/95 backdrop-blur-xl border-b border-border/60 shadow-md transition-all duration-300 space-y-3">
          <LotSelector />
          <TabSwitcher activeTab={activeViewTab} onTabChange={setActiveViewTab} />
        </div>

        {/* Content based on active tab */}
        {activeViewTab === "live" ? (
          <>
            <ParkingLotMap />

            {/* Notify Button */}
            <button 
              onClick={handleNotifyClick}
              className={`w-full rounded-2xl py-4 font-semibold flex items-center justify-center gap-3 shadow-primary-glow hover:opacity-90 transition-opacity active:scale-[0.98] ${
                isWaitingForSpot 
                  ? "bg-secondary text-secondary-foreground animate-pulse" 
                  : "bg-primary text-primary-foreground"
              }`}
            >
              <span>{isWaitingForSpot ? "מבטל התראה..." : "התראה על פינוי מקום"}</span>
              <Bell className="w-5 h-5" />
            </button>



          </>
        ) : (
          <PredictionsView />
        )}
      </main>
    </div>
  );
};

export default Index;
