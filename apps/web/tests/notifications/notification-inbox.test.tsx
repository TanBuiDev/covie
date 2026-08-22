import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { NotificationInbox } from "@/features/notifications/notification-inbox";
const { inbox, markRead, markAllRead }=vi.hoisted(()=>({inbox:vi.fn(),markRead:vi.fn(),markAllRead:vi.fn()})); vi.mock("@/features/notifications/notification-api",()=>({inbox,markRead,markAllRead}));
it("marks a notification read without exposing backend fallback content",async()=>{inbox.mockResolvedValue({items:[{id:"n1",type:"ORDER_CONFIRMED",read:false,createdAt:"2026-01-01T00:00:00Z",data:{}}]});render(<NotificationInbox/>);await waitFor(()=>expect(screen.getByText("Đơn hàng đã được xác nhận")).toBeInTheDocument());fireEvent.click(screen.getByText("Đơn hàng đã được xác nhận"));await waitFor(()=>expect(markRead).toHaveBeenCalledWith("n1"));});
