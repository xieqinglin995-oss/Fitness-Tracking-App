import json, math, sys
from pathlib import Path
from datetime import date, timedelta

if len(sys.argv) != 2:
    raise SystemExit('Usage: python generate-history.py OUTPUT_DIRECTORY')
OUT=Path(sys.argv[1]).resolve()
OUT.mkdir(parents=True,exist_ok=True)
END=date(2026,11,12)

def generate(years):
    data={'weights':[],'plans':[],'executions':[],'templates':[], 'settings':{'unit':'JIN','goalGrams':75000,'loadUnit':'KG'}}
    start=END.replace(year=END.year-years)+timedelta(days=1)
    days=(END-start).days+1
    for i in range(days):
        d=start+timedelta(days=i)
        ds=d.isoformat();ts=ds+'T08:00:00+08:00'
        data['weights'].append({'id':'w-'+ds,'date':ds,'grams':80000+round(1200*math.sin(i/35))+i%70,'note':'验收模拟数据','createdAt':ts,'updatedAt':ts})
        if d.weekday() not in (0,2,4): continue
        p={'id':'p-'+ds,'date':ds,'name':'力量与有氧','rest':False,'exercises':[]}
        for j,name in enumerate(('深蹲','卧推','划船','拉伸','平板支撑','跑步')):
            kind='STRENGTH' if j<3 else 'DURATION' if j<5 else 'DISTANCE'
            target={'sets':[{'reps':10,'loadGrams':20000+j*5000} for _ in range(3)] if j<3 else [],'seconds':None if j<3 else 600.0 if j<5 else 1800.0,'kilometers':5.0 if j==5 else None}
            eid='i-'+ds+'-'+str(j)
            p['exercises'].append({'id':eid,'name':name,'kind':kind,'target':target,'note':'','temporary':False})
            data['executions'].append({'id':'a-'+ds+'-'+str(j),'date':ds,'exerciseId':eid,'nameSnapshot':name,'kindSnapshot':kind,'targetSnapshot':target,'actual':target,'status':'DONE','skipReason':'','updatedAt':ts,'completedSets':[0,1,2] if j<3 else []})
        data['plans'].append(p)
    root={'schemaVersion':3,'exportedAt':'2026-11-12T12:00:00+08:00','counts':{k:len(data[k]) for k in ('weights','plans','executions','templates')},'data':data}
    path=OUT/f'{years}y.json'
    path.write_text(json.dumps(root,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    print(path.name,root['counts'],path.stat().st_size)

for years in (1,5,10): generate(years)
