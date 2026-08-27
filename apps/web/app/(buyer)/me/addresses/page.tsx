import { AddressList } from "@/features/profile/address-list";
import { getSavedAddresses } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function AddressesPage() {
  await requireUser();
  const addresses = await getSavedAddresses();

  return <AddressList addresses={addresses} />;
}
