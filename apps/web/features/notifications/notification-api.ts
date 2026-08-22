import type { components } from "@covie/api-client"; import { createApiClient } from "@/lib/api/api-client";
export type Notification=components["schemas"]["NotificationResponse"];
export async function inbox(cursor?:string){const {data,response}=await createApiClient().GET("/api/v1/me/notifications",{params:{query:cursor?{cursor}:{}}});if(!response.ok||!data)throw new Error("Không thể tải thông báo.");return data;}
export async function markRead(notificationId:string){const {response}=await createApiClient().POST("/api/v1/me/notifications/{notificationId}/mark-read",{params:{path:{notificationId}}});if(!response.ok)throw new Error("Không thể cập nhật thông báo.");}
export async function markAllRead(){const {response}=await createApiClient().POST("/api/v1/me/notifications/mark-all-read");if(!response.ok)throw new Error("Không thể cập nhật thông báo.");}
