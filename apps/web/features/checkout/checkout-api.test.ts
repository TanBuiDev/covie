import { afterEach, expect, it, vi } from "vitest";
import { commitCheckout, previewCheckout } from "./checkout-api";
import { invalidateCsrfToken } from "@/features/auth/csrf";
afterEach(()=>{invalidateCsrfToken();vi.unstubAllGlobals();});

function mockError(status:number,code:string) {
  vi.stubGlobal("fetch",vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({headerName:"X-CSRF-Token",token:"fixture"})))
    .mockResolvedValueOnce(new Response(JSON.stringify({error:{code,message:"backend detail",requestId:"fixture-request"}}),{status})));
}

it("preserves CHECKOUT_CHANGED after openapi-fetch has consumed the error response",async()=>{
  mockError(409,"CHECKOUT_CHANGED");
  await expect(commitCheckout("token","stable-key")).rejects.toMatchObject({status:409,code:"CHECKOUT_CHANGED",requestId:"fixture-request"});
});

it("preserves a parsed preview validation error",async()=>{
  mockError(422,"VALIDATION_FAILED");
  await expect(previewCheckout({source:{type:"DIRECT"},items:[{locationVariantId:"offer",quantity:1}],fulfillmentMethod:"PICKUP",paymentMethod:"COD"})).rejects.toMatchObject({status:422,code:"VALIDATION_FAILED"});
});
