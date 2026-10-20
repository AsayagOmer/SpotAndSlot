import { Card } from "@/components/ui/card";
import {
  Table, TableHeader, TableRow, TableHead, TableBody, TableCell,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { createUserAsAdmin } from "@/lib/api";
import { useAdminUsers } from "@/hooks/useParkingData";
import { useAct } from "../hooks";
import { roleStyle } from "../components/shared";
import CreateUserDialog from "../components/CreateUserDialog";

const Users = () => {
  const usersQ = useAdminUsers();
  const act = useAct();
  const users = usersQ.data ?? [];

  return (
    <>
      <div className="flex justify-end">
        <CreateUserDialog onCreate={(input) => act("User created", () => createUserAsAdmin(input))} />
      </div>
      <Card className="p-0 overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12"></TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((u) => (
              <TableRow key={u.userId.email}>
                <TableCell className="text-xl">{u.avatar || "🙂"}</TableCell>
                <TableCell className="font-medium">{u.username || "—"}</TableCell>
                <TableCell className="text-muted-foreground">{u.userId.email}</TableCell>
                <TableCell>
                  <Badge variant="outline" className={roleStyle(u.role)}>{u.role}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {users.length === 0 && !usersQ.isLoading && (
              <TableRow><TableCell colSpan={4} className="text-center text-muted-foreground py-8">No users.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
    </>
  );
};

export default Users;
