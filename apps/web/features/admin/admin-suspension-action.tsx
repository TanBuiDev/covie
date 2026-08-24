"use client";

import { useState } from "react";

export function AdminSuspensionAction({ status, onSubmit }: { status: string; onSubmit: (operation: "suspend" | "restore", reason?: string) => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const suspended = status === "SUSPENDED";
  async function submit() {
    setBusy(true);
    try { await onSubmit(suspended ? "restore" : "suspend", suspended ? undefined : reason); }
    finally { setBusy(false); }
  }
  return <span style={{ display: "inline-flex", gap: 6 }}><input aria-label="Administrative reason" placeholder="Reason code" value={reason} onChange={event => setReason(event.target.value)} disabled={suspended} /><button disabled={busy || (!suspended && !reason)} onClick={() => void submit()}>{suspended ? "Restore" : "Suspend"}</button></span>;
}
