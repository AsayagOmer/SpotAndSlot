import { useMemo } from "react";
import { format } from "date-fns";
import { Card } from "@/components/ui/card";
import {
  Table, TableHeader, TableRow, TableHead, TableBody, TableCell,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useAdminCommands } from "@/hooks/useParkingData";

// Command-history feed, newest first. This is an ADMIN view over the Admin API;
// ADMINs may not read objects, so targets are shown by id.
const Activity = () => {
  const commandsQ = useAdminCommands();
  const commands = commandsQ.data ?? [];

  const recentCommands = useMemo(
    () => [...commands].sort((a, b) =>
      new Date(b.invocationTimestamp ?? 0).getTime() - new Date(a.invocationTimestamp ?? 0).getTime()),
    [commands],
  );

  return (
    <Card className="p-0 overflow-hidden">
      <ScrollArea className="h-[calc(100vh-180px)]">
        <Table>
          <TableHeader className="sticky top-0 bg-card z-10">
            <TableRow>
              <TableHead className="w-40">Time</TableHead>
              <TableHead>Command</TableHead>
              <TableHead>Invoked by</TableHead>
              <TableHead>Target</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {recentCommands.map((c, i) => {
              const targetId = c.targetObject?.id?.objectId;
              return (
                <TableRow key={c.id?.commandId ?? i}>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">
                    {c.invocationTimestamp ? format(new Date(c.invocationTimestamp), "dd/MM HH:mm:ss") : "—"}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className="font-mono text-[11px]">{c.command}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">{c.invokedBy?.userId.email ?? "—"}</TableCell>
                  <TableCell className="text-sm">
                    <span className="text-muted-foreground font-mono text-xs">{targetId?.slice(0, 8) ?? "—"}</span>
                  </TableCell>
                </TableRow>
              );
            })}
            {recentCommands.length === 0 && !commandsQ.isLoading && (
              <TableRow><TableCell colSpan={4} className="text-center text-muted-foreground py-8">No commands recorded yet.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </ScrollArea>
    </Card>
  );
};

export default Activity;
