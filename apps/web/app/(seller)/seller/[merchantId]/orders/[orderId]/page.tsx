import { OrderDetailView } from "@/features/orders/order-detail";
export default async function MerchantOrderPage({params}:{params:Promise<{orderId:string}>}){const {orderId}=await params;return <main className="orders-surface"><OrderDetailView orderId={orderId}/></main>;}
