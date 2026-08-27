import { AdminOrderHistory } from "@/features/admin/admin-order-history";
export default async function OrderHistoryPage({params}:{params:Promise<{orderId:string}>}){const {orderId}=await params;return <AdminOrderHistory orderId={orderId}/>;}
