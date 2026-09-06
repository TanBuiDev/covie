"""Build the Vietnamese academic report using the bundled python-docx runtime.
Only reads repository sources and writes within docs/academic-report.
"""
from pathlib import Path
import collections, hashlib, json, math, re, shutil, subprocess
from datetime import datetime
from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.shared import Pt, Inches, Twips, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[1]
ASSETS = BASE / 'assets'
ASSETS.mkdir(exist_ok=True)
OUT = BASE / 'CoVie_BaoCaoDoAn.docx'
WIDTH = 9071
FONT = 'Times New Roman'
HEAD = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
SHORT = HEAD[:7]

# Named academic overrides of standard_business_brief; editorial_cover header.
TOKENS = {
    'preset':'standard_business_brief', 'header_pattern':'editorial_cover',
    'named_override':'academic_vietnamese_report',
    'page_twips':[11906,16838], 'margins_twips':{'top':1134,'bottom':1134,'left':1701,'right':1134},
    'header_footer_twips':708, 'content_width_twips':WIDTH,
    'body':{'font':FONT,'size_pt':13,'before_pt':0,'after_pt':6,'line':1.3,'alignment':'justified'},
    'headings':{'h1':[16,16,8],'h2':[14,12,6],'h3':[13,8,4],'color':'000000'},
    'tables':{'font_size_pt':10.5,'line':1.1,'after_pt':3,'width_twips':WIDTH,'indent_twips':120,'cell_margins_twips':[80,80,120,120],'fill':'F2F4F7'},
    'caption':{'font_size_pt':11,'before_pt':4,'after_pt':6},
    'override_reason':'Long-form Vietnamese academic report: A4, binding margin, Times New Roman, black headings, larger body type. No institutional template supplied.'
}
(BASE/'style-tokens.json').write_text(json.dumps(TOKENS,ensure_ascii=False,indent=2),encoding='utf-8')

FONTPATH = Path('C:/Windows/Fonts')
def imfont(size=29,bold=False):
    return ImageFont.truetype(str(FONTPATH/('arialbd.ttf' if bold else 'arial.ttf')),size)

def canvas(height):
    im=Image.new('RGB',(1600,height),'white')
    return im,ImageDraw.Draw(im)

def wrapped(draw,text,font,max_width):
    from terminology import quote
    text=quote(text)
    output=[]
    for para in text.split('\n'):
        line=''
        for word in para.split(' '):
            candidate=(line+' '+word).strip()
            if draw.textlength(candidate,font=font)>max_width and line:
                output.append(line);line=word
            else:line=candidate
        output.append(line)
    return output

def box(draw,xy,title,body='',size=30,fill='#F3F6F7',ellipse=False):
    x,y,w,h=xy
    if ellipse: draw.ellipse((x,y,x+w,y+h),fill=fill,outline='#233846',width=3)
    else: draw.rounded_rectangle((x,y,x+w,y+h),radius=16,fill=fill,outline='#233846',width=3)
    entries=[(s,imfont(size,True)) for s in wrapped(draw,title,imfont(size,True),w-38)]
    if body: entries += [(s,imfont(size-3)) for s in wrapped(draw,body,imfont(size-3),w-38)]
    lineheight=size+10
    assert len(entries)*lineheight <= h-16, (title,len(entries),h)
    top=y+(h-len(entries)*lineheight)/2
    for i,(s,f) in enumerate(entries):
        draw.text((x+(w-draw.textlength(s,font=f))/2,top+i*lineheight),s,font=f,fill='#152935')

def arrow(draw,points,label=None,label_pos=None):
    draw.line(points,fill='#40515C',width=4)
    (x0,y0),(x1,y1)=points[-2:]
    a=math.atan2(y1-y0,x1-x0)
    draw.polygon([(x1,y1),(x1-17*math.cos(a-.45),y1-17*math.sin(a-.45)),(x1-17*math.cos(a+.45),y1-17*math.sin(a+.45))],fill='#40515C')
    if label:
        from terminology import quote
        label=quote(label)
        px,py=label_pos
        f=imfont(24)
        tw=draw.textlength(label,font=f)
        draw.rectangle((px-5,py-3,px+tw+5,py+30),fill='white')
        draw.text((px,py),label,font=f,fill='#263846')

def diagram_assets():
    im,d=canvas(960)
    d.text((45,25),'TÁC NHÂN VÀ NHÓM CHỨC NĂNG COVIE',font=imfont(36,True),fill='#142D3E')
    rows=[('Khách','Tìm kiếm, xem catalog\nvà hồ sơ công khai'),('Người mua','Địa chỉ, giỏ, checkout, đơn\nnhu cầu và thông báo'),('Người bán','Điểm bán, catalog, tồn kho\nxử lý đơn và phản hồi nhu cầu'),('Quản trị viên','Tài khoản, kiểm duyệt\ncấu hình và audit')]
    for i,(a,b) in enumerate(rows):
        y=115+i*195
        box(d,(60,y,320,130),a)
        box(d,(620,y,860,150),b,ellipse=True)
        arrow(d,[(380,y+65),(620,y+65)])
    im.save(ASSETS/'actors.png')

    im,d=canvas(1280)
    box(d,(370,30,860,125),'Trình duyệt','Buyer / Seller / Admin')
    arrow(d,[(800,155),(800,225)],'HTTP / cookie / CSRF',(825,170))
    box(d,(320,225,960,160),'Web — Next.js / React','apps/web • điều hướng, biểu mẫu, trạng thái')
    arrow(d,[(800,385),(800,460)],'Client sinh từ OpenAPI',(830,404))
    box(d,(150,460,1300,275),'API — Spring Boot, monolith phân theo miền','Auth • Users • Merchant • Catalog\nCart • Checkout • Inventory • Orders • Needs\nReview • Reputation • Notifications • Admin')
    for x in [290,800,1310]: arrow(d,[(800,735),(800,785),(x,785),(x,855)])
    box(d,(60,855,460,185),'PostgreSQL / PostGIS','Dữ liệu giao dịch\nphiên, outbox, audit')
    box(d,(570,855,460,185),'Redis','Hạ tầng hỗ trợ\ntheo cấu hình ứng dụng')
    box(d,(1080,855,460,185),'MinIO / S3','Lưu đối tượng media')
    box(d,(150,1120,1300,105),'CI, script và runbook','Kiểm thử • migration • health • backup • rollback',size=27)
    im.save(ASSETS/'architecture.png')

    im,d=canvas(1520)
    d.text((40,18),'“ERD” RÚT GỌN — QUAN HỆ KHÓA NGOẠI CHÍNH',font=imfont(32,True),fill='#142D3E')
    b={
      'users':(50,95,420,125),'merchant_profiles':(585,95,420,125),
      'merchant_locations':(585,350,420,130),'catalog_items':(1120,95,420,125),
      'variants':(1120,350,420,130),'location_variants':(1120,625,420,150),
      'carts':(50,625,420,125),'cart_items':(50,900,420,125),
      'orders':(585,900,420,125),'order_items':(1120,900,420,125),
      'inventory_balances':(1120,1210,420,125),'inventory_reservations':(585,1210,420,125),
    }
    for k,xy in b.items():box(d,xy,k,size=26)
    arrow(d,[(795,220),(795,350)],'1 → 0..N',(815,265))
    arrow(d,[(1005,155),(1120,155)],'1:N',(1020,110))
    arrow(d,[(1330,220),(1330,350)],'1 → 0..N',(1350,265))
    arrow(d,[(1330,480),(1330,625)],'1 → 0..N',(1350,530))
    arrow(d,[(1005,415),(1060,415),(1060,700),(1120,700)],'1:N',(1010,550))
    arrow(d,[(260,220),(260,625)],'1 → 0..N',(280,380))
    arrow(d,[(260,750),(260,900)],'1 → 0..N',(280,800))
    arrow(d,[(470,150),(520,150),(520,965),(585,965)],'buyer 1:N',(480,830))
    arrow(d,[(1005,965),(1120,965)],'1:N',(1020,920))
    arrow(d,[(1330,775),(1330,900)],'1:N',(1350,820))
    arrow(d,[(1540,700),(1570,700),(1570,1270),(1540,1270)],'1:0..1',(1410,1130))
    arrow(d,[(1330,1025),(1330,1120),(795,1120),(795,1210)],'order item 1:N',(840,1135))
    d.text((60,1410),'Mũi tên: từ bản ghi được tham chiếu tới bảng chứa khóa ngoại.',font=imfont(27),fill='#263846')
    d.text((60,1450),'Đã lược bớt quan hệ phụ; “schema” đầy đủ nằm trong “migration” V001–V013.',font=imfont(26),fill='#263846')
    im.save(ASSETS/'erd.png')

    im,d=canvas(1640)
    labels=['Người mua / Web','Checkout API','PostgreSQL']
    xs=[210,800,1380]
    for x,label in zip(xs,labels):
        box(d,(x-185,20,370,100),label,size=29)
        d.line((x,125,x,1560),fill='#A5AFB7',width=3)
    steps=[(0,1,'1. Yêu cầu preview'),(1,2,'2. Resolve nguồn, đọc offer và địa chỉ'),(2,1,'3. Dữ liệu hiện tại'),(1,2,'4. Lưu phiên + snapshot chấp nhận'),(1,0,'5. Token, thời hạn, tiền đã tính'),(0,1,'6. Commit token'),(1,2,'7. Khóa phiên / nguồn / tồn kho'),(1,2,'8. Revalidate và so sánh snapshot'),(1,2,'9. Tạo đơn + reservation + outbox'),(1,2,'10. Tiêu thụ phiên, xử lý giỏ; COMMIT'),(1,0,'11. Mã đơn; PENDING_CONFIRMATION')]
    for i,(a,b,label) in enumerate(steps):
        y=190+i*113
        arrow(d,[(xs[a],y),(xs[b],y)])
        f=imfont(26)
        lines=wrapped(d,label,f,abs(xs[a]-xs[b])-25)
        for j,line in enumerate(lines):d.text((min(xs[a],xs[b])+12,y-35-30*(len(lines)-1-j)),line,font=f,fill='#152935')
    box(d,(75,1450,1450,140),'Nhánh không thành công','Có thay đổi: báo CHECKOUT_CHANGED, xem trước lại.\nLỗi trong transaction: rollback; không báo tạo đơn thành công.',size=28,fill='#FCF6EC')
    im.save(ASSETS/'checkout.png')

    im,d=canvas(1330)
    box(d,(475,35,650,120),'PENDING_CONFIRMATION',size=28)
    arrow(d,[(800,155),(800,240)],'Xác nhận hợp lệ',(830,180))
    box(d,(475,240,650,110),'CONFIRMED',size=30)
    for x in [280,800,1320]:arrow(d,[(800,350),(800,405),(x,405),(x,470)])
    box(d,(50,470,460,160),'Giao hàng','OUT_FOR_DELIVERY',size=28)
    box(d,(570,470,460,160),'Đến nhận','READY_FOR_PICKUP\n(bước tùy luồng)',size=28)
    box(d,(1090,470,460,160),'Dịch vụ','Đánh dấu đã thực hiện',size=28)
    arrow(d,[(280,630),(280,695),(600,695),(600,770)])
    arrow(d,[(1320,630),(1320,695),(1000,695),(1000,770)])
    box(d,(420,770,760,120),'AWAITING_BUYER_CONFIRMATION',size=28)
    arrow(d,[(800,890),(800,1020)],'Xác nhận / timeout theo policy',(820,930))
    box(d,(520,1020,560,105),'COMPLETED',size=30)
    arrow(d,[(800,630),(800,710),(350,710),(350,1070),(520,1070)],'Xác nhận nhận hàng',(50,920))
    box(d,(75,1185,1450,110),'Nhánh khác: từ chối, hết hạn, hủy, sự cố, không đến nhận','Sơ đồ lược giản; quyền và đường chuyển hợp lệ do backend quyết định.',size=27,fill='#FCF6EC')
    im.save(ASSETS/'orderstates.png')
    for k,name in {'home':'pub-001-home-390.png','product':'pub-006-item-detail-390.png','success':'buy-006-checkout-success-390.png'}.items():
        shutil.copyfile(ROOT/'.local/dev/visual-review'/name,ASSETS/(k+'.png'))

diagram_assets()

# Snapshot of sources: content hashes, never environment values or credentials.
paths=[ROOT/'AGENTS.md',ROOT/'README.md',ROOT/'docs/CoVie-project-status.md',ROOT/'apps/web/package.json',ROOT/'apps/api/pom.xml',ROOT/'packages/api-client/openapi/openapi.json']
paths+=sorted((ROOT/'apps/api/src/main/resources/db/migration').glob('*.sql'))
paths+=[ROOT/'apps/api/src/main/java/vn/covie'/p for p in ['checkout/application/CheckoutPreviewService.java','checkout/application/CheckoutCommitService.java','shipping/application/ShippingEvaluator.java','orders/model/OrderStatus.java']]
api=json.loads((ROOT/'packages/api-client/openapi/openapi.json').read_text(encoding='utf-8'))
migrations=sorted((ROOT/'apps/api/src/main/resources/db/migration').glob('*.sql'))
all_tables={}
for f in migrations:
    sql=f.read_text(encoding='utf-8')
    for m in re.finditer(r'create\s+table\s+(\w+)\s*\((.*?)\n\);',sql,re.I|re.S):
        all_tables[m.group(1)]=(f.name,m.group(2))
methods={'get','post','put','patch','delete','head','options','trace'}
inventory={'date':'2026-09-04','commit':HEAD,'openapi_version':api.get('openapi'),
 'api_path_count':len(api['paths']),'api_operation_count':sum(k.lower() in methods for p in api['paths'].values() for k in p),
 'migration_count':len(migrations),'create_table_count':len(all_tables),
 'api_test_java_file_count':len(list((ROOT/'apps/api/src/test').rglob('*Test.java'))),
 'sources':[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths],
 'application_tests_run_in_report_session':False}
(BASE/'evidence-manifest.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')

appendix=['# PHỤ LỤC B. MIGRATION VÀ TỪ ĐIỂN DỮ LIỆU CHỌN LỌC','',
 '## B.1. Danh mục migration','',
 'Danh mục được trích trực tiếp từ các file migration tại commit '+SHORT+'. Bảng này kiểm kê hiện vật trên đĩa, không xác nhận một cơ sở dữ liệu đang chạy đã áp dụng thành công.','',
 '| Migration | Số CREATE TABLE |','| --- | --- |']
for f in migrations:
    appendix.append('| '+f.name+' | '+str(sum(x[0]==f.name for x in all_tables.values()))+' |')
appendix+=['','Tổng cộng '+str(len(all_tables))+' khai báo bảng trong '+str(len(migrations))+' migration. Các migration sau có thể ALTER bảng hoặc constraint được tạo trước. Vì vậy, từ điển sau ghi rõ đây là các cột chọn lọc theo câu lệnh tạo ban đầu, không phải bản dump schema cuối cùng.','',
 '## B.2. Các bảng theo migration','']
for f in migrations:
    names=[k for k,v in all_tables.items() if v[0]==f.name]
    appendix+=['**'+f.name+'**',', '.join(names) if names else 'Không tạo bảng mới; xem các thay đổi khác trong migration.','']

selected={
 'users':('Tài khoản người dùng','id status display_name created_at updated_at version'),
 'merchant_profiles':('Đơn vị bán hàng hoặc cung cấp dịch vụ','id owner_user_id name status created_at version'),
 'merchant_locations':('Điểm bán và khả năng thực hiện','id merchant_profile_id name address_text geo_point pickup_enabled seller_delivery_enabled service_enabled minimum_delivery_order version'),
 'catalog_items':('Mô tả mặt hàng','id merchant_profile_id item_type listing_kind name status primary_category_id'),
 'location_variants':('Chào bán tại một điểm bán','id merchant_location_id variant_id price currency_code inventory_mode is_available version'),
 'inventory_balances':('Tồn và lượng đã giữ','location_variant_id stock_on_hand reserved_quantity updated_at version'),
 'carts':('Giỏ theo người, điểm bán và loại hàng','id user_id merchant_location_id cart_type cart_status version'),
 'checkout_sessions':('Phiên chấp nhận checkout','id token_hash user_id source_type cart_version request_snapshot preview_snapshot expires_at consumed_at'),
 'orders':('Đơn và tiền tại thời điểm giao dịch','id order_code order_type buyer_user_id merchant_location_id status source_type payment_method subtotal shipping_fee known_total total_amount version'),
 'order_items':('Dòng đơn và dữ liệu lịch sử','id order_id location_variant_id item_type quantity unit_price line_total item_snapshot'),
 'need_requests':('Nhu cầu phía người mua','id buyer_user_id need_type category_id status expires_at version'),
 'reviews':('Đánh giá theo giao dịch','id order_item_id buyer_user_id rating comment created_at')}
for ordinal,(name,(desc,fields)) in enumerate(selected.items(),1):
    file,body=all_tables[name]
    appendix += [f'## B.{ordinal+2}. {name} — {desc}','',f'Nguồn tạo bảng: {file}. Các trường dưới được chọn theo vai trò của bảng; xem migration để có đầy đủ khóa ngoại, chỉ mục và thay đổi bổ sung.','', '| Cột | Kiểu SQL | Thuộc tính tại CREATE TABLE |','| --- | --- | --- |']
    existing={}
    for line in body.splitlines():
        match=re.match(r'\s*(\w+)\s+((?:varchar|char|numeric)\([^)]*\)|geography\([^)]*\)|geometry\([^)]*\)|\w+)(.*)',line)
        if match and match.group(1).lower() not in {'constraint','primary','foreign','unique','check','or','and'}:
            existing[match.group(1)]=(match.group(2),match.group(3).strip().rstrip(','))
    for field in fields.split():
        if field in existing:
            typ,attrs=existing[field]
            labels=[]
            if 'primary key' in attrs.lower():labels.append('PK')
            labels.append('NOT NULL' if 'not null' in attrs.lower() or 'primary key' in attrs.lower() else 'Cho phép NULL theo khai báo')
            fk=re.search(r'references\s+(\w+)\((\w+)\)',attrs,re.I)
            if fk:labels.append('FK → '+fk.group(1)+'.'+fk.group(2))
            if 'unique' in attrs.lower():labels.append('UNIQUE')
            appendix.append(f'| {field} | {typ} | '+ '; '.join(labels)+' |')
    appendix.append('')

appendix+=['# PHỤ LỤC C. DANH MỤC API TRONG SNAPSHOT','',
 'Hợp đồng lưu có '+str(inventory['api_path_count'])+' path và '+str(inventory['api_operation_count'])+' operation HTTP, OpenAPI '+str(api.get('openapi'))+'. Danh sách được sinh từ file hợp đồng, không phải kết quả gọi thử mọi endpoint. Header, body, quyền và response schema phải đọc ở operation tương ứng; không suy ra quyền chỉ từ tên path.','']
groups=collections.defaultdict(list)
for path,ops in sorted(api['paths'].items()):
    group=path.removeprefix('/api/v1/').split('/')[0]
    groups[group].append((path,', '.join(k.upper() for k in ops if k.lower() in methods)))
for idx,(group,items) in enumerate(sorted(groups.items()),1):
    appendix += [f'## C.{idx}. Nhóm {group}','', '| Method | Đường dẫn |','| --- | --- |']
    appendix += [f'| {ms} | {path} |' for path,ms in items]
    appendix.append('')
appendix += ['# PHỤ LỤC D. GHI NHẬN PHIÊN BẢN VÀ HÌNH MINH HỌA','',
 'Báo cáo được tổng hợp ngày 04/09/2026 từ commit '+HEAD+'. Bộ đếm schema, API và file test là kết quả đọc tĩnh. Các kết quả ứng dụng ngày 26/08/2026 được dẫn theo tài liệu nội bộ, không được chạy lại trong phiên biên soạn.','',
 'Các sơ đồ tác nhân, kiến trúc, ERD rút gọn, checkout và trạng thái đơn được tác giả bản thảo dựng lại để giải thích code; không phải hình export từ Figma. Hình Home, Product Detail và Checkout Success là ảnh fixture local có sẵn, dùng nguyên ảnh và ghi nhãn nguồn. Không dùng ngày sửa file để khẳng định thời gian chụp.','',
 'Logo HUTECH trên bìa được lấy từ website chính thức của Trường Đại học Công nghệ TP.HCM. Công cụ ChatGPT/Codex được sử dụng để hỗ trợ tổng hợp tài liệu, diễn đạt, xây dựng sơ đồ và dàn trang báo cáo. Các mốc kiểm chứng trong nội dung được giữ theo nguồn; mốc 07/2026 trên bìa không thay thế ngày của bằng chứng kỹ thuật.','']
(BASE/'chapters/08-phu-luc-tu-dong.md').write_text('\n'.join(appendix),encoding='utf-8')

doc=Document()
sec=doc.sections[0]
sec.page_width=Twips(11906);sec.page_height=Twips(16838)
sec.top_margin=sec.bottom_margin=Twips(1134)
sec.left_margin=Twips(1701);sec.right_margin=Twips(1134)
sec.header_distance=sec.footer_distance=Twips(708)
sec.different_first_page_header_footer=True

def font_style(style,size,bold=False,color='000000'):
    style.font.name=FONT;style.font.size=Pt(size);style.font.bold=bold;style.font.color.rgb=RGBColor.from_string(color)
    rpr=style.element.get_or_add_rPr()
    rf=rpr.find(qn('w:rFonts'))
    if rf is None:rf=OxmlElement('w:rFonts');rpr.insert(0,rf)
    for attr in ['ascii','hAnsi','eastAsia','cs']:rf.set(qn('w:'+attr),FONT)
    lang=OxmlElement('w:lang');lang.set(qn('w:val'),'vi-VN');rpr.append(lang)

for name in ['Normal','Title','Subtitle','Heading 1','Heading 2','Heading 3','Caption','Header','Footer']:
    st=doc.styles[name];font_style(st,13)
    pf=st.paragraph_format;pf.space_before=Pt(0);pf.space_after=Pt(6);pf.line_spacing=1.3;pf.widow_control=True
doc.styles['Normal'].paragraph_format.alignment=WD_ALIGN_PARAGRAPH.JUSTIFY
for lev,size,before,after in [(1,16,16,8),(2,14,12,6),(3,13,8,4)]:
    st=doc.styles[f'Heading {lev}'];font_style(st,size,True)
    pf=st.paragraph_format;pf.space_before=Pt(before);pf.space_after=Pt(after);pf.keep_with_next=True;pf.alignment=WD_ALIGN_PARAGRAPH.LEFT
    pf.page_break_before=(lev==1)
font_style(doc.styles['Title'],24,True)
font_style(doc.styles['Subtitle'],16)
font_style(doc.styles['Caption'],11)
doc.styles['Caption'].paragraph_format.alignment=WD_ALIGN_PARAGRAPH.CENTER
doc.styles['Caption'].paragraph_format.space_before=Pt(4)
for name,size in [('Table Text',10.5),('Code Block',9.5),('TOC Entry',11),('Source Note',10.5),('Cover Meta',13)]:
    st=doc.styles.add_style(name,1);font_style(st,size)
    pf=st.paragraph_format;pf.space_before=Pt(0);pf.space_after=Pt(3);pf.line_spacing=1.1;pf.widow_control=True
    pf.alignment=WD_ALIGN_PARAGRAPH.LEFT
doc.styles['Source Note'].paragraph_format.space_before=Pt(4)
doc.styles['Source Note'].paragraph_format.space_after=Pt(4)
doc.styles['Code Block'].font.name='Consolas'
doc.styles['TOC Entry'].paragraph_format.space_after=Pt(4)
doc.styles['Cover Meta'].paragraph_format.space_after=Pt(10)

for style_name in ['Header','Footer']:
    font_style(doc.styles[style_name],10,color='666666')
    doc.styles[style_name].paragraph_format.line_spacing=1
hp=sec.header.paragraphs[0];hp.text='';hp.alignment=WD_ALIGN_PARAGRAPH.RIGHT
fp=sec.footer.paragraphs[0];fp.alignment=WD_ALIGN_PARAGRAPH.CENTER
fp.add_run('Trang ')
for kind,value in [('fldChar','begin'),('instrText',' PAGE '),('fldChar','separate'),('t','2'),('fldChar','end')]:
    r=OxmlElement('w:r');el=OxmlElement('w:'+kind)
    if kind=='fldChar':el.set(qn('w:fldCharType'),value)
    else:el.text=value
    if kind=='instrText':el.set(qn('xml:space'),'preserve')
    r.append(el);fp._p.append(r)
update=OxmlElement('w:updateFields');update.set(qn('w:val'),'true');doc.settings.element.append(update)
doc.core_properties.title='CoVie — Báo cáo đồ án: nền tảng mua bán địa phương'
doc.core_properties.subject='Phân tích, thiết kế, triển khai và đánh giá hệ thống CoVie'
doc.core_properties.author='[Sinh viên thực hiện — cần bổ sung]'
doc.core_properties.keywords='CoVie, báo cáo đồ án, marketplace, checkout, PostgreSQL'

def inline(p,text):
    # No shell substitution; content is authored text.
    for i,part in enumerate(re.split(r'(\*\*.*?\*\*|`[^`]+`)',text)):
        if part.startswith('**') and part.endswith('**'):p.add_run(part[2:-2]).bold=True
        elif part.startswith('`') and part.endswith('`'):p.add_run(part[1:-1])
        else:p.add_run(part)
    return p

def paragraph(text,style=None):return inline(doc.add_paragraph(style=style),text)

def center(text,size=13,bold=False,before=0,after=8):
    p=doc.add_paragraph(style='Cover Meta');p.alignment=WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before=Pt(before);p.paragraph_format.space_after=Pt(after)
    r=p.add_run(text);r.font.size=Pt(size);r.bold=bold
    return p

center('TRƯỜNG ĐẠI HỌC CÔNG NGHỆ TP.HCM',15,True,after=8)
center('KHOA CÔNG NGHỆ THÔNG TIN',14,True,after=5)
center('Chuyên ngành: Công nghệ phần mềm',13,after=12)
p=center('',after=22)
shape=p.add_run().add_picture(str(BASE/'assets/hutech-logo.png'),width=Inches(3.1))
shape._inline.docPr.set('descr','Logo HUTECH từ website chính thức của trường')
center('BÁO CÁO ĐỒ ÁN',22,True,after=24)
center('XÂY DỰNG NỀN TẢNG\nMUA BÁN ĐỊA PHƯƠNG COVIE',22,True,after=18)
center('Ứng dụng kiến trúc web phân lớp\nvà xử lý giao dịch nhất quán',15,after=40)
for x in ['Sinh viên thực hiện: Bùi Lê Hoàng Tấn','Mã số sinh viên: 2280602879','Lớp: 22DTHH2']:
    p=paragraph(x,'Cover Meta');p.paragraph_format.left_indent=Twips(900)
center('Hồ Chí Minh, 07/2026',13,before=45)

def front_heading(title):
    p=doc.add_paragraph(title,style='Heading 1')
    # These are preliminary pages, excluded from the generated main-body TOC.
    return p

front_heading('LỜI CAM ĐOAN VÀ LỜI CẢM ƠN')
paragraph('Lời cam đoan. Tôi là Bùi Lê Hoàng Tấn, mã số sinh viên 2280602879, lớp 22DTHH2, Khoa Công nghệ thông tin, chuyên ngành Công nghệ phần mềm, Trường Đại học Công nghệ TP.HCM. Với báo cáo “Xây dựng nền tảng mua bán địa phương CoVie”, tôi cam kết trình bày trung thực phạm vi thực hiện, nguồn tham khảo, kết quả và những hạn chế của đề tài.')
paragraph('Các công nghệ, thư viện, tài liệu và mã nguồn được sử dụng cần được ghi nhận đúng nguồn. Kết quả kiểm thử lịch sử, phân tích mã nguồn và kịch bản kiểm thử đề xuất được phân biệt trong báo cáo; những phần chưa có bằng chứng nghiệm thu không được xem là đã hoàn thành. Báo cáo có sử dụng ChatGPT/Codex để hỗ trợ tổng hợp tài liệu, diễn đạt nội dung, xây dựng sơ đồ và dàn trang. Tôi chịu trách nhiệm rà soát nội dung và việc sử dụng các công cụ hỗ trợ khi nộp báo cáo.')
paragraph('Lời cảm ơn. Tôi xin trân trọng cảm ơn quý thầy cô Trường Đại học Công nghệ TP.HCM và Khoa Công nghệ thông tin. Những kiến thức về lập trình, cơ sở dữ liệu, phân tích thiết kế hệ thống và kiểm thử phần mềm là nền tảng để tôi tiếp cận bài toán xây dựng CoVie một cách có hệ thống.')
paragraph('Tôi cũng xin ghi nhận đóng góp của cộng đồng phát triển các công nghệ và thư viện mã nguồn mở được sử dụng trong đề tài. Tôi mong nhận được nhận xét của hội đồng về tính đúng đắn của thiết kế, mức độ đáp ứng yêu cầu và chất lượng kiểm chứng, để tiếp tục hoàn thiện hệ thống và năng lực chuyên môn của bản thân.')
paragraph('Sinh viên thực hiện\nBùi Lê Hoàng Tấn')

front_heading('TÓM TẮT')
paragraph('CoVie là đề tài xây dựng nền tảng mua bán địa phương nhằm tổ chức thông tin nguồn cung và nhu cầu, hỗ trợ người mua tìm kiếm và đặt hàng, người bán vận hành điểm bán, và quản trị viên theo dõi hoạt động hệ thống. Đề tài tập trung vào chuỗi khám phá, giỏ hàng, xem trước giao dịch, tạo đơn và thực hiện đơn, đồng thời mở rộng tới yêu cầu cần mua, đánh giá, uy tín và thông báo.')
paragraph('Hệ thống sử dụng Next.js và React cho giao diện, Spring Boot và Java cho API, PostgreSQL/PostGIS cho dữ liệu và các thành phần hỗ trợ Redis, MinIO. Mô hình tách mặt hàng, lựa chọn và chào bán tại điểm bán; checkout tái kiểm tra dữ liệu và khóa hàng tồn khi commit; sự kiện giao dịch được ghi nhận qua outbox. Hợp đồng OpenAPI được dùng để kết nối web với API và hỗ trợ kiểm tra sự nhất quán của các bên.')
paragraph('Tài liệu dự án ghi nhận các kết quả kiểm thử local ở baseline ngày 26/08/2026. Bản báo cáo đối chiếu thêm mã nguồn c477f30 và xác định các giới hạn về tích hợp khoảng cách/coverage, độ đầy đủ snapshot, hợp đồng giao diện và bằng chứng runtime mới. Kết quả được trình bày như một nền tảng đã triển khai và có kiểm chứng cục bộ lịch sử; chưa được kết luận sẵn sàng production hoặc đã kiểm nghiệm tác động với người dùng thật.')
paragraph('Từ khóa: mua bán địa phương; marketplace; checkout; kiểm soát đồng thời; PostgreSQL; hợp đồng API.')

front_heading('ABSTRACT')
paragraph('CoVie is a local marketplace project that structures nearby supply and buying demand. It supports buyers, merchant members and administrators through discovery, carts, checkout, order fulfillment, need requests, reviews and notifications. The project emphasizes transactional consistency and resource-level authorization alongside user interface development.')
paragraph('The implementation combines Next.js and React, Spring Boot and Java, PostgreSQL/PostGIS, Redis and S3-compatible storage. Catalog descriptions, variants and location-specific offers are separated. Checkout revalidates accepted data and locks tracked inventory before creating an order, while an outbox records transactional event intents. OpenAPI provides a shared contract for the web client and backend.')
paragraph('Historical project records report local verification on 26 August 2026. This report also inspects source snapshot c477f30 and identifies limitations in geographic checkout integration, historical snapshots, UI contracts and current runtime evidence. The result is an implemented software foundation with historical local verification, rather than a claim of production readiness or demonstrated commercial impact.')

front_heading('DANH MỤC TỪ VIẾT TẮT')

tables=[]
def table(rows,caption=None,widths=None):
    if caption:
        p=paragraph(caption,'Caption');p.paragraph_format.keep_with_next=True
        tables.append(caption)
    n=len(rows[0]); widths=widths or [WIDTH//n]*(n-1)+[WIDTH-(WIDTH//n)*(n-1)]
    tb=doc.add_table(rows=1,cols=n);tb.autofit=False
    pr=tb._tbl.tblPr
    for tag in ['tblW','tblInd','tblLayout','tblCellMar','tblBorders']:
        old=pr.find(qn('w:'+tag))
        if old is not None:pr.remove(old)
    for tag,attrs in [('tblW',{'w':str(WIDTH),'type':'dxa'}),('tblInd',{'w':'120','type':'dxa'}),('tblLayout',{'type':'fixed'})]:
        el=OxmlElement('w:'+tag)
        for k,v in attrs.items():el.set(qn('w:'+k),v)
        pr.append(el)
    mar=OxmlElement('w:tblCellMar')
    for side,val in [('top',80),('bottom',80),('start',120),('end',120)]:
        el=OxmlElement('w:'+side);el.set(qn('w:w'),str(val));el.set(qn('w:type'),'dxa');mar.append(el)
    pr.append(mar)
    borders=OxmlElement('w:tblBorders')
    for side in ['top','left','bottom','right','insideH','insideV']:
        el=OxmlElement('w:'+side);el.set(qn('w:val'),'single');el.set(qn('w:sz'),'4');el.set(qn('w:color'),'B8C0C6');borders.append(el)
    pr.append(borders)
    grid=tb._tbl.tblGrid
    for child in list(grid):grid.remove(child)
    for w in widths:
        el=OxmlElement('w:gridCol');el.set(qn('w:w'),str(w));grid.append(el)
    for idx,values in enumerate(rows):
        row=tb.rows[0] if idx==0 else tb.add_row()
        trpr=row._tr.get_or_add_trPr()
        cant=OxmlElement('w:cantSplit');trpr.append(cant)
        if idx==0:
            repeat=OxmlElement('w:tblHeader');trpr.append(repeat)
        for col,(cell,value) in enumerate(zip(row.cells,values)):
            cell.width=Twips(widths[col]);cell._tc.get_or_add_tcPr().find(qn('w:tcW')).set(qn('w:w'),str(widths[col]))
            p=cell.paragraphs[0];p.style=doc.styles['Table Text'];inline(p,value)
            if idx==0:
                for run in p.runs:run.bold=True
                shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'F2F4F7');cell._tc.get_or_add_tcPr().append(shade)
    doc.add_paragraph().paragraph_format.space_after=Pt(3)
    return tb

table([['Từ viết tắt','Diễn giải'],['API','Application Programming Interface — giao diện lập trình ứng dụng'],['PWA','Progressive Web App — ứng dụng web với khả năng tăng cường'],['DTO','Data Transfer Object — đối tượng trao đổi dữ liệu'],['ERD','Entity Relationship Diagram — sơ đồ thực thể và quan hệ'],['JWT','JSON Web Token — định dạng token có chữ ký trong hệ thống'],['CSRF','Cross-Site Request Forgery — giả mạo yêu cầu từ nguồn khác'],['SSE','Server-Sent Events — sự kiện từ máy chủ tới máy khách'],['CI','Continuous Integration — tích hợp liên tục'],['E2E','End-to-end — kiểm thử hành trình xuyên các lớp'],['CRUD','Create, Read, Update, Delete — tạo, đọc, cập nhật, xóa'],['P95','Phân vị 95 của tập độ trễ đo được'],['HEAD','Commit hiện tại của checkout Git'],['Fixture','Dữ liệu thử có chủ đích để tái lập kịch bản']],[ ][0] if False else None,widths=[1500,WIDTH-1500])

front_heading('MỤC LỤC')
toc_anchor=doc.add_paragraph('Mục lục điện tử bên dưới liên kết tới các phần. Trong Word, cập nhật trường mục lục để bổ sung số trang theo bố cục trên máy sử dụng.',style='Source Note')
toc_marker=doc.add_paragraph()
front_heading('DANH MỤC HÌNH VÀ BẢNG')
list_marker=doc.add_paragraph()

figs={
 'actors':('3.1','Tác nhân và nhóm chức năng của CoVie',6.0),
 'architecture':('4.1','Kiến trúc tổng thể và trách nhiệm các thành phần',6.0),
 'erd':('4.2','ERD rút gọn của các bảng giao dịch chính',6.0),
 'checkout':('4.3','Trình tự preview và commit checkout',5.8),
 'orderstates':('4.4','Các nhánh chính của vòng đời đơn hàng',5.9),
 'home':('5.1','Trang chủ trên điện thoại — dữ liệu fixture local',2.75),
 'product':('5.2','Chi tiết sản phẩm — dữ liệu fixture local',2.75),
 'success':('5.3','Thông báo đã tạo đơn — dữ liệu fixture local',2.75)}
headings=[];figcaptions=[];chapter='';tcounter=collections.Counter();bookmark=0

def heading(text,level):
    global bookmark,chapter
    p=doc.add_paragraph(text,style=f'Heading {min(level,3)}')
    bookmark+=1;name='section_'+str(bookmark)
    start=OxmlElement('w:bookmarkStart');start.set(qn('w:id'),str(bookmark));start.set(qn('w:name'),name)
    end=OxmlElement('w:bookmarkEnd');end.set(qn('w:id'),str(bookmark));p._p.insert(0,start);p._p.append(end)
    if level<=2:headings.append((text,level,name))
    if level==1:
        m=re.search(r'CHƯƠNG (\d+)|PHỤ LỤC ([A-Z])',text)
        chapter=(m.group(1) or m.group(2)) if m else 'TK'
    return p

def figure(key):
    number,title,width=figs[key]
    p=doc.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.keep_with_next=True
    picture=p.add_run().add_picture(str(ASSETS/(key+'.png')),width=Inches(width))
    picture._inline.docPr.set('descr',title)
    cap='Hình '+number+'. '+title
    paragraph(cap,'Caption');figcaptions.append(cap)
    source='Nguồn: giao diện CoVie với dữ liệu thử nghiệm.' if key in {'home','product','success'} else 'Nguồn: sơ đồ giải thích do bản báo cáo dựng từ mã nguồn và tài liệu CoVie; đã lược giản để dễ đọc.'
    paragraph(source,'Source Note')

def markdown(text):
    lines=text.splitlines();i=0
    while i<len(lines):
        s=lines[i].strip()
        if not s:i+=1;continue
        if s.startswith('```'):
            i+=1
            while i<len(lines) and not lines[i].strip().startswith('```'):
                paragraph(lines[i],'Code Block');i+=1
            i+=1;continue
        if s.startswith('#'):
            h=re.match(r'^(#{1,3})\s+(.*)',s)
            if h:heading(h.group(2),len(h.group(1)))
            i+=1;continue
        m=re.fullmatch(r'\[\[FIGURE:(\w+)\]\]',s)
        if m:figure(m.group(1));i+=1;continue
        if s.startswith('|'):
            rows=[]
            while i<len(lines) and lines[i].strip().startswith('|'):
                cells=[x.strip() for x in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r'[:\- ]+',x) for x in cells):rows.append(cells)
                i+=1
            tcounter[chapter]+=1
            # Caption uses the latest real section title; no placeholder table labels.
            title=headings[-1][0] if headings else 'Bảng dữ liệu'
            title=re.sub(r'^\w+(?:\.\w+)*\.\s*','',title)
            cap=f'Bảng {chapter}.{tcounter[chapter]}. {title}'
            n=len(rows[0]);widths=None
            if rows[0]==['Method','Đường dẫn']:widths=[1300,WIDTH-1300]
            elif rows[0][0]=='Cột':widths=[2750,1450,WIDTH-4200]
            elif n==4:widths=[900,2800,3450,WIDTH-7150]
            elif n==2:widths=[int(WIDTH*.57),WIDTH-int(WIDTH*.57)]
            table(rows,cap,widths)
            continue
        para=[s];i+=1
        while i<len(lines) and lines[i].strip() and not lines[i].lstrip().startswith(('#','|','```','[[FIGURE:')):
            para.append(lines[i].strip());i+=1
        paragraph(' '.join(para))

chapterfiles=sorted((BASE/'chapters').glob('*.md'))
for f in chapterfiles:markdown(f.read_text(encoding='utf-8'))

def insert_link_before(anchor,text,target,indent=0):
    p=OxmlElement('w:p');ppr=OxmlElement('w:pPr');style=OxmlElement('w:pStyle');style.set(qn('w:val'),'TOCEntry');ppr.append(style)
    ind=OxmlElement('w:ind');ind.set(qn('w:left'),str(indent));ppr.append(ind);p.append(ppr)
    link=OxmlElement('w:hyperlink');link.set(qn('w:anchor'),target)
    r=OxmlElement('w:r');t=OxmlElement('w:t');t.text=text;r.append(t);link.append(r);p.append(link)
    anchor.addprevious(p)

# Build a Word TOC field with useful linked cached content, not fake page numbers.
startp=OxmlElement('w:p');run=OxmlElement('w:r');begin=OxmlElement('w:fldChar');begin.set(qn('w:fldCharType'),'begin');run.append(begin);startp.append(run)
run=OxmlElement('w:r');ins=OxmlElement('w:instrText');ins.set(qn('xml:space'),'preserve');ins.text=' TOC \\o "1-2" \\h \\z \\u ';run.append(ins);startp.append(run)
run=OxmlElement('w:r');sep=OxmlElement('w:fldChar');sep.set(qn('w:fldCharType'),'separate');run.append(sep);startp.append(run)
toc_marker._p.addprevious(startp)
for title,level,name in headings:insert_link_before(toc_marker._p,title,name,220 if level==2 else 0)
run=OxmlElement('w:r');end=OxmlElement('w:fldChar');end.set(qn('w:fldCharType'),'end');run.append(end);toc_marker._p.append(run)
for title in figcaptions+tables:
    p=OxmlElement('w:p');pp=OxmlElement('w:pPr');st=OxmlElement('w:pStyle');st.set(qn('w:val'),'TOCEntry');pp.append(st);p.append(pp)
    rr=OxmlElement('w:r');tt=OxmlElement('w:t');tt.text=title;rr.append(tt);p.append(rr);list_marker._p.addprevious(p)

from cover_layout import apply_cover
apply_cover(doc)
from table_layout import apply_tables
apply_tables(doc)
from editorial_revision import apply_editorial
apply_editorial(doc)
from product_expansion import apply_product_expansion
apply_product_expansion(doc)
from usecase_expansion import apply_usecases
apply_usecases(doc)
from visual_expansion import apply_visual_expansion
apply_visual_expansion(doc)
doc.save(OUT)
body='\n\n'.join(f.read_text(encoding='utf-8') for f in chapterfiles)
(BASE/'CoVie_BaoCaoDoAn.md').write_text('# CoVie — Báo cáo đồ án\n\nBản nội dung đồng hành với DOCX; thông tin bìa và mục lục định dạng nằm trong DOCX.\n\n'+body,encoding='utf-8')
summary={'output':str(OUT),'characters':len(body),'whitespace_tokens_vietnamese_not_linguistic_words':len(body.split()),'chapters':7,'figures':sum(p.style.name=='Caption' and p.text.startswith('Hình ') for p in doc.paragraphs),'tables':len(doc.tables),'headings_h1_h2':len(headings),'api_paths':inventory['api_path_count'],'api_operations':inventory['api_operation_count'],'create_tables':len(all_tables),'render_qa':'PENDING'}
(BASE/'build-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
