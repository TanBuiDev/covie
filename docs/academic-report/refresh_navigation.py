"""Refresh cached page references from a rendered PDF without changing styles."""
from pathlib import Path
import argparse,json,re,unicodedata,zipfile
from lxml import etree
import pdfplumber

p=argparse.ArgumentParser();p.add_argument('--pdf',required=True);args=p.parse_args()
b=Path(__file__).resolve().parent
data=json.loads((b/'navigation-pages.json').read_text(encoding='utf-8'))
norm=lambda s:re.sub(r'\s+','',unicodedata.normalize('NFC',s)).casefold()
with pdfplumber.open(args.pdf) as pdf:pages=[norm(p.extract_text() or '') for p in pdf.pages]
changes=[]
for key,e in data['entries'].items():
    found=[i+1 for i,t in enumerate(pages) if norm(e['text']) in t]
    assert found,(key,e)
    if e['page']!=found[-1]:changes.append([key,e['page'],found[-1]])
    e['page']=found[-1]
data['source_pdf']=str(Path(args.pdf).resolve())
(b/'navigation-pages.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
f=b/'CoVie_BaoCaoDoAn.docx'
with zipfile.ZipFile(f) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
with zipfile.ZipFile(f,'w') as z:
    for i,raw in items:
        if i.filename=='word/document.xml':
            root=etree.fromstring(raw)
            for fld in root.xpath('//w:fldSimple[contains(@w:instr,"PAGEREF")]',namespaces=ns):
                key=fld.get('{'+ns['w']+'}instr').split()[1]
                ts=fld.xpath('.//w:t',namespaces=ns);ts[0].text=str(data['entries'][key]['page'])
            raw=etree.tostring(root,xml_declaration=True,encoding='UTF-8',standalone=True)
        z.writestr(i,raw)
print(json.dumps({'page_count':len(pages),'changed_references':len(changes)}))
