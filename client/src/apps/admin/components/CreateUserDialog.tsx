import { useState } from "react";
import { Loader2, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";

// Admins create users of any role; the server verifies the acting admin.
const CreateUserDialog = ({
  onCreate,
}: {
  onCreate: (u: { email: string; password: string; username: string; avatar: string; role: string }) => Promise<unknown>;
}) => {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [avatar, setAvatar] = useState("🙂");
  const [role, setRole] = useState("END_USER");
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      // the server requires non-blank username and avatar
      await onCreate({ email: email.trim(), password, username: username.trim(), avatar: avatar.trim(), role });
      setOpen(false);
      setEmail(""); setUsername(""); setPassword(""); setAvatar("🙂"); setRole("END_USER");
    } catch { /* toast shown by act() */ }
    finally { setBusy(false); }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline"><UserPlus className="w-4 h-4 mr-1.5" /> Add user</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Create a user</DialogTitle></DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="u-email">Email</Label>
            <Input id="u-email" type="email" placeholder="user@example.com" value={email}
              onChange={(e) => setEmail(e.target.value)} autoFocus />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-name">Name</Label>
            <Input id="u-name" value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-avatar">Avatar</Label>
            <Input id="u-avatar" className="w-24 text-center text-lg" maxLength={4}
              value={avatar} onChange={(e) => setAvatar(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="u-pass">Password</Label>
            <Input id="u-pass" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <p className="text-xs text-muted-foreground">At least 5 chars, incl. a digit and a special char.</p>
          </div>
          <div className="space-y-1.5">
            <Label>Role</Label>
            <Select value={role} onValueChange={setRole}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="END_USER">END_USER</SelectItem>
                <SelectItem value="OPERATOR">OPERATOR</SelectItem>
                <SelectItem value="ADMIN">ADMIN</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button onClick={submit} disabled={busy || !email.trim() || !password || !username.trim() || !avatar.trim()}>
            {busy && <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />} Create user
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default CreateUserDialog;
