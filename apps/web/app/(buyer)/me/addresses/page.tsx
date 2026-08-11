import { AddressForm } from "@/features/profile/address-form";
import { getSavedAddresses } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function AddressesPage() {
  await requireUser();
  const addresses = await getSavedAddresses();
  return <main>
    <h1>Địa chỉ đã lưu</h1>
    <ul>{addresses.map((address) => <li key={address.id}><strong>{address.label}</strong><p>{address.addressText}</p><p>{address.latitude}, {address.longitude}</p></li>)}</ul>
    <AddressForm />
  </main>;
}
