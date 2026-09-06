from pathlib import Path
import json
from terminology import quote
BASE=Path(__file__).resolve().parent
REVISIONS=json.loads((BASE/'academic-prose-revisions.json').read_text(encoding='utf-8'))
def revise(text):
    plain=text.replace('“','').replace('”','')
    for prefix,value in REVISIONS.items():
        if plain.startswith(prefix):return quote(value)
    return text
def apply(doc):
    changes=[]
    paragraphs=list(doc.paragraphs)
    seen=set()
    for table in doc.tables:
        for row in table.rows:
            for cell in row.cells:
                if cell._tc not in seen:
                    seen.add(cell._tc);paragraphs.extend(cell.paragraphs)
    for p in paragraphs:
        old=p.text;new=revise(old)
        if old!=new:
            nodes=p._p.xpath('.//w:t')
            if nodes:
                nodes[0].text=new
                for n in nodes[1:]:n.text=''
                changes.append({'before':old,'after':new})
    return changes
