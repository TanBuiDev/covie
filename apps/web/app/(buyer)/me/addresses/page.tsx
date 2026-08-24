import { AddressForm } from "@/features/profile/address-form";
import { AddressList } from "@/features/profile/address-list";
import { getSavedAddresses } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function AddressesPage() {
  await requireUser();
  const addresses = await getSavedAddresses();

  return (
    <main className="buyer-surface">
      <header className="surface-header">
        <p className="eyebrow">Tài khoản / Địa chỉ</p>
        <h1>Địa chỉ đã lưu</h1>
        <p>Địa chỉ cá nhân chỉ được đọc qua API tài khoản và không được đưa vào cache offline.</p>
      </header>
      <AddressList addresses={addresses} />
      <AddressForm />
    </main>
  );
}
