import { AddressList } from "@/features/profile/address-list";

export default function AddressesLoading() {
  return <AddressList addresses={[]} state="loading" />;
}
