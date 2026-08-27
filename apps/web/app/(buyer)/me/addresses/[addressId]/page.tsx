import { notFound } from "next/navigation";
import { AddressForm } from "@/features/profile/address-form";
import { getSavedAddresses } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function EditAddressPage({ params }: { params: Promise<{ addressId: string }> }) {
  await requireUser();
  const { addressId } = await params;
  const addresses = await getSavedAddresses();
  const address = addresses.find((item) => item.id === addressId);
  if (!address) notFound();
  return <AddressForm address={address} />;
}
