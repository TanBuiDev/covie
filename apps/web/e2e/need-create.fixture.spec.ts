import { expect, test } from "@playwright/test";
test.use({channel:"chrome",trace:"off",video:"off"});
test.skip(process.env.COVIE_NEED_CREATE_FIXTURE !== "1","Opt-in synthetic create/read fixture; no real data writes.");

test("Need form handles empty addresses, past expiry, expired sessions and pending type changes",async({page})=>{
  test.setTimeout(60_000);
  let mode:"empty"|"ready"|"expired"="empty";
  let serviceGate:Promise<void>|undefined;
  let creates=0;
  const unsafe:string[]=[];
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();
    const url=new URL(request.url());
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    const authError={error:{code:"AUTH_REQUIRED",message:"opaque"}};
    if(url.pathname==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(url.pathname==="/api/v1/auth/refresh")return json(401,authError);
    if(url.pathname==="/api/v1/categories"){
      const service=url.searchParams.get("type")==="SERVICE";
      if(service && serviceGate)await serviceGate;
      return json(200,[{id:service?"service-category":"product-category",name:service?"Sửa chữa":"Rau"}]);
    }
    if(url.pathname==="/api/v1/me/addresses")return mode==="expired"?json(401,authError):json(200,mode==="empty"?[]:[{id:"address-1",addressText:"Nhà",defaultAddress:true}]);
    if(url.pathname==="/api/v1/me/need-requests" && request.method()==="POST") {creates++;return json(401,authError);}
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){unsafe.push(`${request.method()} ${url.pathname}`);return route.abort();}
    return route.continue();
  });
  const submit=page.getByRole("button",{name:"Đăng Cần mua"});
  const login=page.getByRole("link",{name:"Đăng nhập lại"});
  for(const width of [1280,1366,1536,360,390]){
    await page.setViewportSize({width,height:width<500?844:768});
    mode="empty";
    await page.goto("/needs/new");
    if(width<1024)await expect(page.getByRole("navigation",{name:"Điều hướng chính"})).toBeHidden();
    await expect(page.getByRole("link",{name:"Quản lý địa chỉ"})).toBeVisible();
    await expect(submit).toBeDisabled();
    mode="ready";
    await page.reload();
    await expect(submit).toBeEnabled();
    const title=page.getByRole("textbox",{name:/Tên \/ nhu cầu/});
    await title.fill("Tìm rau");
    await page.getByRole("textbox",{name:/Từ khóa tìm kiếm/}).fill("rau");
    await page.getByLabel(/Thời hạn/).fill("2000-01-01T12:00");
    const previous=creates;
    await page.locator("form.need-form").evaluate(form=>form.dispatchEvent(new Event("submit",{bubbles:true,cancelable:true})));
    await expect(page.locator("form.need-form").getByRole("alert")).toContainText("Thời hạn");
    expect(creates).toBe(previous);
    await page.getByLabel(/Thời hạn/).fill("2099-01-01T12:00");
    await submit.click();
    await expect(login).toHaveAttribute("href","/login?returnTo=%2Fneeds%2Fnew");
    await expect(title).toHaveValue("Tìm rau");
    await expect(submit).toBeDisabled();
    mode="expired";
    await page.reload();
    await expect(login).toBeVisible();
    await expect(submit).toBeDisabled();
    mode="ready";
    await page.reload();
    await expect(submit).toBeEnabled();
    let release!:()=>void;
    serviceGate=new Promise(resolve=>{release=resolve;});
    const serviceRequest=page.waitForRequest(request=>request.url().includes("/api/v1/categories") && new URL(request.url()).searchParams.get("type")==="SERVICE");
    await page.getByRole("button",{name:"Dịch vụ",exact:true}).click();
    await serviceRequest;
    await expect(submit).toBeDisabled();
    await expect(page.getByRole("combobox",{name:/Danh mục/})).toBeDisabled();
    release();serviceGate=undefined;
    await expect(submit).toBeEnabled();
    await expect(page.getByRole("combobox",{name:/Danh mục/})).toHaveValue("service-category");
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  }
  expect(unsafe).toEqual([]);
});

test("Need form retries prerequisite reads and validates/locks create across viewport sizes",async({page})=>{
  test.setTimeout(60_000);
  let failRead=true;
  let creates=0;
  const unsafe:string[]=[];
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();
    const path=new URL(request.url()).pathname;
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    if(path==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(path==="/api/v1/categories")return failRead?json(503,{error:{code:"ERROR",message:"private"}}):json(200,[{id:"category-1",name:"Rau"}]);
    if(path==="/api/v1/me/addresses")return json(200,[{id:"address-1",addressText:"Nhà",defaultAddress:true}]);
    if(path==="/api/v1/me/need-requests" && request.method()==="POST"){
      creates++;
      expect(request.postDataJSON()).toMatchObject({requestedQuantity:2,addressId:"address-1",categoryId:"category-1"});
      return json(503,{error:{code:"ERROR",message:"private backend detail"}});
    }
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){unsafe.push(`${request.method()} ${path}`);return route.abort();}
    return route.continue();
  });
  for(const width of [1280,1366,1536,360,390]){
    await page.setViewportSize({width,height:width<500?844:768});
    failRead=true;
    await page.goto("/needs/new");
    await expect(page.getByRole("button",{name:"Thử lại",exact:true})).toBeVisible();
    const title=page.getByRole("textbox",{name:/Tên \/ nhu cầu/});
    await title.fill("Tìm rau");
    failRead=false;
    await page.getByRole("button",{name:"Thử lại",exact:true}).click();
    const submit=page.getByRole("button",{name:"Đăng Cần mua"});
    await expect(submit).toBeEnabled();
    await expect(title).toHaveValue("Tìm rau");
    await page.getByRole("textbox",{name:/Từ khóa tìm kiếm/}).fill("rau");
    await page.getByLabel(/Thời hạn/).fill("2099-01-01T12:00");
    await page.getByRole("spinbutton",{name:/Số lượng/}).fill("1.5");
    await page.locator("form.need-form").evaluate(form=>form.dispatchEvent(new Event("submit",{bubbles:true,cancelable:true})));
    await expect(page.locator("form.need-form").getByRole("alert")).toContainText("Số lượng");
    const previous=creates;
    await page.getByRole("spinbutton",{name:/Số lượng/}).fill("2");
    await page.locator("form.need-form").evaluate(form=>{
      form.dispatchEvent(new Event("submit",{bubbles:true,cancelable:true}));
      form.dispatchEvent(new Event("submit",{bubbles:true,cancelable:true}));
    });
    await expect(page.locator("form.need-form").getByRole("alert")).toContainText("Không thể tạo nhu cầu");
    expect(creates-previous).toBe(1);
    await expect(page.getByText("private backend detail",{exact:true})).toHaveCount(0);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  }
  expect(unsafe).toEqual([]);
});
