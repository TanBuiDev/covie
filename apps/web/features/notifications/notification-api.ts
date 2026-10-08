import type { components } from "@covie/api-client"; import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";
export type Notification=components["schemas"]["NotificationResponse"];
export async function inbox(cursor?:string){const {data,error,response}=await createApiClient().GET("/api/v1/me/notifications",{params:{query:cursor?{cursor}:{}}});if(!response.ok||!data)throw await parsedApiError(response,error);return data;}
export async function markRead(notificationId:string){const {error,response}=await createApiClient().POST("/api/v1/me/notifications/{notificationId}/mark-read",{params:{path:{notificationId}}});if(!response.ok)throw await parsedApiError(response,error);}
export async function markAllRead(){const {error,response}=await createApiClient().POST("/api/v1/me/notifications/mark-all-read");if(!response.ok)throw await parsedApiError(response,error);}
