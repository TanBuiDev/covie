import { expect, test } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

test.use({channel:"chrome",trace:"off",video:"off"});
test.skip(process.env.COVIE_GOOGLE_LOCAL_AUDIT !== "1","Requires local API with unconfigured Google; reads only.");

test("Google actions follow configuration readiness across Auth routes without starting OAuth",async({page})=>{
  test.setTimeout(90_000);
  const directory=resolve("../../.local/buyer-ui-audit/auth-capability");
  mkdirSync(directory,{recursive:true});
  const writes:string[]=[];
  const errors:string[]=[];
  const captures:object[]=[];
  page.on("pageerror",error=>errors.push(error.message));
  await page.route("**/api/v1/**",route=>{
    const request=route.request();
    const path=new URL(request.url()).pathname;
    if (!["GET","HEAD","OPTIONS"].includes(request.method()) || path.includes("/auth/google/start") || path.includes("/auth/oauth2/")) {
      writes.push(`${request.method()} ${path}`);
      return route.abort("blockedbyclient");
    }
    return route.continue();
  });
  const status=await page.request.get("/api/v1/auth/google/status");
  expect(status.status()).toBe(200);
  expect(await status.json()).toEqual({configured:false});
  for(const viewport of [{width:1280,height:720},{width:1366,height:768},{width:1536,height:864},{width:360,height:800},{width:390,height:844}]) {
    await page.setViewportSize(viewport);
    for(const path of ["/login","/register","/onboarding/password","/reset-password"]) {
      await page.goto(path);
      await expect(page.getByText(/Google hiện chưa được cấu hình/)).toBeVisible();
      await expect(page.getByRole("button",{name:"Tiếp tục với Google",exact:true})).toBeDisabled();
      await expect(page.locator("a[href*='/api/v1/auth/google/start']")).toHaveCount(0);
      if(path === "/login") await expect(page.getByRole("button",{name:"Đăng nhập",exact:true})).toBeEnabled();
      const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
      expect(overflow).toBe(false);
      const icon=await page.getByRole("button",{name:"Tiếp tục với Google",exact:true}).locator("img").boundingBox();
      expect(icon!.width).toBe(20);expect(icon!.height).toBe(20);
      captures.push({viewport,path,overflow});
      await page.screenshot({path:resolve(directory,`${path.replaceAll("/","-")}-${viewport.width}.png`),fullPage:false});
    }
  }
  // These are browser-only status fixtures; no OAuth start or Google request is sent.
  let attempts=0;
  let configuredFixture=false;
  await page.route("**/api/v1/auth/google/status",route=>{
    attempts++;
    return !configuredFixture ? route.fulfill({status:503,json:{error:{code:"FIXTURE_UNAVAILABLE"}}}) : route.fulfill({json:{configured:true}});
  });
  await page.goto("/login");
  await expect(page.getByRole("button",{name:"Tiếp tục với Google",exact:true})).toBeDisabled();
  await expect(page.getByRole("button",{name:"Thử lại Google",exact:true})).toBeVisible();
  const previousAttempts=attempts;
  configuredFixture=true;
  await page.getByRole("button",{name:"Thử lại Google",exact:true}).click();
  await expect(page.getByRole("link",{name:"Tiếp tục với Google",exact:true})).toHaveAttribute("href","/api/v1/auth/google/start?intent=LOGIN");
  await expect(page.getByRole("link",{name:"Quên mật khẩu?",exact:true})).toHaveAttribute("href","/api/v1/auth/google/start?intent=PASSWORD_RESET");
  await expect(page.getByRole("link",{name:"Tạo tài khoản bằng Google",exact:true})).toHaveAttribute("href","/api/v1/auth/google/start?intent=LOGIN");
  expect(attempts).toBeGreaterThan(previousAttempts);
  expect(writes).toEqual([]);expect(errors).toEqual([]);
  writeFileSync(resolve(directory,"observations.json"),JSON.stringify({liveStatus:{configured:false},configuredEvidence:"Browser-only fixture; no real Google authentication",captures,writes,errors},null,2));
});
