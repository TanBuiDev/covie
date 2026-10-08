import { AddressForm } from "@/features/profile/address-form";
import { requireUser } from "@/lib/auth/require-user";

export default async function NewAddressPage() {
  await requireUser("/me/addresses/new");
  return <AddressForm />;
}
