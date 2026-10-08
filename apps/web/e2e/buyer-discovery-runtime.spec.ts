import {expect,test} from "@playwright/test";

test.use({channel:"chrome",video:"off"});
const food="65d8f44f-06a0-5774-a4e8-d3f94cc26056";

// Guest GET-only acceptance against the approved recovered local runtime.
for(const viewport of [{width:1280,height:720},{width:1366,height:768},{width:1536,height:864}]) {
  test(`real discovery filters, paging and navigation at ${viewport.width}x${viewport.height}`,async({page,request})=>{
    await page.setViewportSize(viewport);
    await page.addInitScript(()=>localStorage.setItem("covie:browse-location:v1",JSON.stringify({version:1,
      location:{kind:"coordinates",lat:10.84,lng:106.84,label:"Vinhomes Grand Park"}})));
    await page.goto(`/search?categoryId=${food}&sort=DISTANCE`);
    await expect(page.locator("article")).toHaveCount(10);
    await page.getByRole("button",{name:"Bộ lọc",exact:true}).click();
    await page.getByLabel("Giá từ (₫)").fill("50000");
    await page.getByLabel("Giá đến (₫)").fill("100000");
    await page.getByRole("button",{name:"Áp dụng",exact:true}).click();
    await expect(page).toHaveURL(/minPrice=50000/);
    const reference=await request.get(`http://localhost:8081/api/v1/catalog/items?lat=10.84&lng=106.84&radiusMeters=5000&sort=DISTANCE&categoryId=${food}&minPrice=50000&maxPrice=100000`);
    expect(reference.ok()).toBeTruthy();
    const expected=(await reference.json()).page.items;
    await expect(page.locator("article")).toHaveCount(expected.length);
    for(const item of expected) await expect(page.getByRole("heading",{name:item.name,exact:true})).toBeVisible();
    await page.getByRole("button",{name:"Bộ lọc",exact:true}).click();
    await expect(page.getByLabel("Giá từ (₫)")).toHaveValue("50000");
    await page.getByRole("button",{name:"Đặt lại",exact:true}).click();
    await page.getByRole("button",{name:"Áp dụng",exact:true}).click();
    await expect(page.locator("article")).toHaveCount(20);
    await page.getByRole("button",{name:"Tải thêm kết quả",exact:true}).click();
    await expect(page.locator("article")).toHaveCount(40);
    await page.getByRole("button",{name:"Tải thêm kết quả",exact:true}).click();
    await expect(page.locator("article")).toHaveCount(45);
    const hrefs=await page.locator("article h2 a").evaluateAll(links=>links.map(link=>link.getAttribute("href")));
    expect(new Set(hrefs).size).toBe(45);
    await expect(page.getByRole("button",{name:"Tải thêm kết quả",exact:true})).toHaveCount(0);
    const search=page.getByRole("searchbox",{name:"Tìm món đồ, dịch vụ"});
    await search.fill("cơm"); await search.press("Enter");
    await expect(page).toHaveURL(/q=c%C6%A1m/);
    await expect(search).toHaveValue("cơm");
    const keywordReference=await request.get("http://localhost:8081/api/v1/catalog/items?lat=10.84&lng=106.84&radiusMeters=5000&q=c%C6%A1m&sort=RELEVANCE");
    expect(keywordReference.ok()).toBeTruthy();
    const keywordItems=(await keywordReference.json()).page.items;
    expect(keywordItems.length).toBeGreaterThan(0);
    await expect(page.locator("article")).toHaveCount(keywordItems.length);
    for(const item of keywordItems) await expect(page.getByRole("heading",{name:item.name,exact:true})).toBeVisible();
    await page.goBack();
    await expect(search).toHaveValue("");
    await expect(page.locator("article")).toHaveCount(20);
    await page.goForward();
    await expect(search).toHaveValue("cơm");
    await expect(page.locator("article")).toHaveCount(keywordItems.length);
    await page.reload();
    await expect(search).toHaveValue("cơm");
    await expect(page.locator("article")).toHaveCount(keywordItems.length);
  });
}
