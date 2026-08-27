import { AdminCapabilityState, AdminPageHeader } from "./admin-ui";

export function AdminCapabilityPage({ title, subtitle, message }: { title: string; subtitle: string; message: string }) {
  return <div className="admin-page-stack"><AdminPageHeader title={title} subtitle={subtitle} /><AdminCapabilityState title="Chưa có Admin contract xác thực" message={message} readOnly /></div>;
}
