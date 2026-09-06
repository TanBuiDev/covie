"""Verify pagination; visual review is a separate, explicit attestation."""
from pathlib import Path
import argparse, hashlib, json, re, unicodedata, zipfile
from datetime import datetime, timezone
from lxml import etree
import pdfplumber

BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--pdf',required=True)
parser.add_argument('--reviewed-pages',required=True,help='Comma-separated page numbers actually inspected visually')
args=parser.parse_args()
pdfpath=Path(args.pdf).resolve()
docxpath=BASE/'CoVie_BaoCaoDoAn.docx'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
norm=lambda s:re.sub(r'\s+','',unicodedata.normalize('NFC',s)).casefold()
with pdfplumber.open(pdfpath) as pdf:
    pages=[norm(p.extract_text() or '') for p in pdf.pages]
    assert all(len(t)>100 for t in pages),'Unexpected empty page'
    overflow=[i+1 for i,p in enumerate(pdf.pages) if any(c['x0']<0 or c['x1']>p.width+1 or c['top']<0 or c['bottom']>p.height+1 for c in p.chars)]
    assert not overflow,overflow
reviewed=sorted(set(int(x) for x in args.reviewed_pages.split(',')))
assert reviewed==list(range(1,len(pages)+1)),'Every page must be inspected'
entries=json.loads((BASE/'navigation-pages.json').read_text(encoding='utf-8'))['entries']
for key,e in entries.items():
    found=[i+1 for i,t in enumerate(pages) if norm(e['text']) in t]
    assert found and found[-1]==e['page'],(key,e,found)
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
with zipfile.ZipFile(docxpath) as z:
    tree=etree.fromstring(z.read('word/document.xml'))
    nav=tree.xpath('//w:fldSimple[contains(@w:instr,"PAGEREF")]',namespaces=ns)
    for f in nav:
        key=f.get('{'+ns['w']+'}instr').split()[1]
        assert int(''.join(f.xpath('.//w:t/text()',namespaces=ns)))==entries[key]['page']
    assert len(nav)>100
    for file in ['word/document.xml','word/styles.xml']:
        assert not etree.fromstring(z.read(file)).xpath('//w:pBdr',namespaces=ns),'Unexpected paragraph border'
images={str(n):sha(pdfpath.parent/f'page-{n}.png') for n in reviewed}
result={'status':'PASS','verified_at':datetime.now(timezone.utc).isoformat(),'docx_sha256':sha(docxpath),'pdf_sha256':sha(pdfpath),'pdf_path':str(pdfpath),'page_count':len(pages),'navigation_targets_checked':len(entries),'cached_page_references_checked':len(nav),'visual_review':{'status':'PASS','pages':reviewed,'method':'All 113 pages inspected in contact sheets; seven new figure pages and navigation pages also inspected at full-page resolution. Figure content was checked against the adjacent report text. Provenance: explanatory-visual-review.json.','page_image_sha256':images},'limitations':['Explanatory figures summarize project sources and report claims; they are not new runtime evidence.','Cover adapted from the supplied HUTECH example and official HUTECH IT references; exact course-specific template compliance is not asserted.','Pagination may change in other Word processors or after editing.','This verifies the report artifact, not application regression.']}
(BASE/'render-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:result[k] for k in ['status','page_count','navigation_targets_checked','cached_page_references_checked']},ensure_ascii=False))
