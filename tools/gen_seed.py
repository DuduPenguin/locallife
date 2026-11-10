#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LocalLife 种子数据生成器（真实商户名单版）

数据来源
--------
商户名称、地址、所属城区来自 **哈尔滨市商务局 2024-02 发布的美食地图**
（https://www.hrbtv.net/folder137/2024-02-15/877233.html 转载），
共 4 个主城区、50+ 家特色餐饮门店，含真实店名与门牌号。

以下字段是**演示用估计值**，不代表真实经营数据：
  · 营业时间   —— 按品类给合理区间
  · 人均价格   —— 按门店类型估算
  · 评分 / 已售 —— 随机生成
  · 经纬度     —— 按商圈中心 + 抖动生成（不是门店真实坐标）

这样做的原因：真实坐标与实时价格需要商业地图 API，公开数据拿不到；
但"哪些店真实存在、在哪条街、卖什么"是公开信息，用它比全编造可信得多。

产出
----
    sql/seed_shops.sql                      导入 MySQL
    locallife-agent/.../content/shops-*.md   按商圈切分的知识库
    locallife-agent/.../eval/questions.json  基于这些商户的评测集

用法
----
    python tools/gen_seed.py
    mysql -h127.0.0.1 -uroot -p123456 locallife --default-character-set=utf8mb4 < sql/seed_shops.sql
"""
import json
import os
import random
from datetime import datetime, timedelta

random.seed(20261110)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AGENT_ROOT = os.path.join(os.path.dirname(ROOT), "locallife-agent")

# 商圈锚点（近似坐标，用于 GEO 演示）
ANCHORS = {
    "中央大街": (126.6168, 45.7730),
    "爱建":     (126.6010, 45.7620),
    "通江街":   (126.6120, 45.7790),
    "群力":     (126.5750, 45.7420),
    "果戈里":   (126.6380, 45.7550),
    "哈工大":   (126.6320, 45.7410),
    "哈西":     (126.6020, 45.7060),
    "巴洛克":   (126.6460, 45.7770),
    "南直路":   (126.6900, 45.7300),
    "香坊":     (126.6830, 45.7200),
}

# 品类：1 美食 2 娱乐 3 KTV 4 丽人 5 健身（与 tb_shop_type 对应）
TYPE_HOURS = {
    1: ["10:00-22:00", "11:00-23:00", "09:00-21:00", "10:30-22:30", "08:00-20:00", "16:00-次日02:00"],
    2: ["10:00-24:00", "09:30-23:30"],
    3: ["18:00-次日02:00", "14:00-次日02:00"],
    4: ["10:00-21:00", "09:30-20:30"],
    5: ["07:00-23:00", "24 小时", "06:30-22:30"],
}
TYPE_PRICE = {1: (3000, 15000), 2: (3500, 6800), 3: (8000, 22000), 4: (5000, 38000), 5: (8000, 22000)}
TYPE_NAME = {1: "美食", 2: "娱乐", 3: "KTV", 4: "丽人", 5: "健身"}

# 真实门店：(名称, 城区, 地址, 商圈锚点, 品类, 特色)
REAL_SHOPS = [
    # ---------------- 道里区 ----------------
    ("波特曼西餐厅", "道里区", "中央大街西七道街 53 号", "中央大街", 1, "俄式西餐，红菜汤和罐焖牛肉是招牌"),
    ("塔道斯西餐厅", "道里区", "中央大街 127 号", "中央大街", 1, "高加索风味西餐，始于 1901 年"),
    ("马迭尔西餐厅", "道里区", "中央大街 112 号", "中央大街", 1, "老字号，楼下的马迭尔冰棍常年排队"),
    ("华梅西餐厅", "道里区", "中央大街 112 号", "中央大街", 1, "俄式大菜，罐牛和奶油烤鱼有名"),
    ("老都一处饭店", "道里区", "中央大街西十三道街 25 号", "中央大街", 1, "三鲜水饺和酱骨"),
    ("东方饺子王", "道里区", "中央大街西七道街 2 号", "中央大街", 1, "连锁饺子馆，酸菜馅最受欢迎"),
    ("哈尔滨天顺源清真餐饮", "道里区", "爱建上海街 104 号", "爱建", 1, "清真菜，手把肉和烤羊排"),
    ("山河屯铁锅炖（通江街）", "道里区", "通江街 178-6 号", "通江街", 1, "铁锅炖大鹅，现炖要等 30 分钟"),
    ("江畔餐厅", "道里区", "通江街与斯大林街交叉口", "通江街", 1, "有江景位，主打东北家常菜"),
    ("欧罗巴", "道里区", "西十一道街 11 号", "中央大街", 1, "西餐和俄式简餐"),
    ("黑宝石西餐厅", "道里区", "汇励广场 C 座", "中央大街", 1, "牛排和意面"),
    ("老味烧烤", "道里区", "安达街 34 号", "中央大街", 1, "老式炭火烤串，晚上人多"),
    ("上墅庭院海鲜烧烤", "道里区", "融江路 3030-8 号", "群力", 1, "海鲜烧烤，带院子可以坐外面"),

    # ---------------- 南岗区 ----------------
    ("老昌春饼", "南岗区", "果戈里大街 11 号", "果戈里", 1, "春饼卷菜，配酸菜白肉"),
    ("东方饺子王（中山路）", "南岗区", "革新街道中山路 214 号", "果戈里", 1, "连锁饺子馆"),
    ("蒙克巴雅尔烤羊腿", "南岗区", "革新街 199 号", "果戈里", 1, "整只烤羊腿，需要提前预订"),
    ("三尺巷东北杀猪菜", "南岗区", "人和街 41-3 号", "果戈里", 1, "杀猪菜和血肠"),
    ("老厨家", "南岗区", "西大直街 40 号", "哈工大", 1, "锅包肉发源店，招牌是锅包肉"),
    ("三嫂铁锅炖家常菜", "南岗区", "贵新街 7 号", "哈工大", 1, "铁锅炖和贴饼子"),
    ("一头牛正宗齐齐哈尔烤肉", "南岗区", "复华四道街 66 号", "哈工大", 1, "齐齐哈尔拌肉，自己烤"),
    ("偏脸子烧烤", "南岗区", "自兴街 51 号", "哈工大", 1, "哈尔滨本地烤串"),
    ("山河屯铁锅炖（自兴街）", "南岗区", "自兴街 71 号", "哈工大", 1, "铁锅炖连锁"),
    ("最东北小锅烀饼", "南岗区", "哈西大街 62 号", "哈西", 1, "小锅烀饼和杀猪菜"),
    ("花里 HANARI", "南岗区", "西城红场 7 楼", "哈西", 1, "日式料理"),
    ("同合居民间菜馆", "南岗区", "万达广场 3 层", "哈西", 1, "东北民间菜"),
    ("厨掌柜", "南岗区", "北兴街 45 号", "哈工大", 1, "家常菜"),
    ("六泰居", "南岗区", "西宁南路 71 号", "哈西", 1, "老字号，京味菜"),
    ("老松滨（西宁南路）", "南岗区", "西宁南路 102 号", "哈西", 1, "东北菜老店"),

    # ---------------- 道外区（中华巴洛克）----------------
    ("北三第一家老太太烧烤总店", "道外区", "南三道街 69 号", "巴洛克", 1, "老太太烧烤，本地人排队"),
    ("清真六合顺", "道外区", "南二道街 20 号", "巴洛克", 1, "清真老字号，牛肉蒸饺"),
    ("黑皮肘子锅包肉", "道外区", "南二道街 E7-03 号", "巴洛克", 1, "肘子和锅包肉"),
    ("张包铺", "道外区", "南二道街 305 号", "巴洛克", 1, "排骨包子，百年老店"),
    ("老街砂锅居", "道外区", "南勋街 307 号", "巴洛克", 1, "砂锅和熏酱"),
    ("大全烧烤", "道外区", "东莱街 86 号", "巴洛克", 1, "烧烤大排档"),
    ("白杨包子", "道外区", "南七道街 149 号", "巴洛克", 1, "早点包子，上午就卖完"),
    ("羊顺斋", "道外区", "丰润街 74 号", "巴洛克", 1, "清真羊肉馆"),
    ("徐包铺", "道外区", "丰润街 17 号", "巴洛克", 1, "包子铺老店"),
    ("春饼小吃", "道外区", "钱塘街 149 号", "巴洛克", 1, "春饼和熏酱"),
    ("府鑫美食", "道外区", "北新街 206 号", "巴洛克", 1, "东北家常菜"),
    ("辣喜涮鲜鱼火锅", "道外区", "礼化街 145 号", "巴洛克", 1, "鲜鱼火锅"),
    ("北十八烤羊排", "道外区", "北十八道街 165 号", "巴洛克", 1, "烤羊排"),
    ("金春大酒店", "道外区", "南直路 781 号", "南直路", 1, "婚宴和家常菜"),
    ("宏盛小馆", "道外区", "淮河路 34 号", "南直路", 1, "小馆家常菜"),

    # ---------------- 香坊区 ----------------
    ("核桃碳烤肉馆", "香坊区", "幸福路 53 号", "香坊", 1, "核桃碳烤肉"),
    ("牧羊人烧烤", "香坊区", "幸福路 39 号", "香坊", 1, "烧烤，羊肉串扎实"),
    ("九转小磨", "香坊区", "三合路 43 号", "香坊", 1, "小磨豆腐和东北菜"),
    ("赫煊馋嘴烧烤", "香坊区", "香滨路 4-21 号 15 门", "香坊", 1, "烧烤，夜宵人多"),
    ("五斗火锅", "香坊区", "和平路 56-2 号", "香坊", 1, "川式火锅"),
    ("160 烧烤", "香坊区", "六顺街 116 号", "香坊", 1, "烧烤，开到凌晨"),
    ("冰城探牛融合烤肉", "香坊区", "菜艺街 70-1 号 -1 层", "香坊", 1, "融合烤肉"),
    ("老松滨饭店", "香坊区", "菜艺街 125 号", "香坊", 1, "东北菜"),
    ("泥大爷烤饼羊汤", "香坊区", "六顺街珠江路小区 107A 栋", "香坊", 1, "烤饼和羊汤"),
    ("灶座小锅烀饼", "香坊区", "湘江路 24 号", "香坊", 1, "小锅烀饼"),
    ("爱上羊烤羊腿旗舰店", "香坊区", "珠江路 095 号", "香坊", 1, "烤羊腿"),
    ("胖子烧烤", "香坊区", "安埠街 351 号", "香坊", 1, "烧烤"),
    ("满汉楼旗舰店", "香坊区", "公滨路 300 号", "香坊", 1, "满族菜和杀猪菜"),
    ("灶台福铁锅炖", "香坊区", "南直路 17 号", "南直路", 1, "铁锅炖"),
    ("善嘉农大朝鲜烤盘", "香坊区", "铁东街道互助街 59 号", "香坊", 1, "朝鲜族烤盘"),

    # ---------------- 其他品类（连锁品牌）----------------
    ("万达影城（哈西店）", "南岗区", "哈西万达广场 4 层", "哈西", 2, "IMAX 厅和杜比全景声厅"),
    ("横店影视城（中央大街店）", "道里区", "中央大街 88 号", "中央大街", 2, "学生票半价，周二全天优惠"),
    ("华臣影城（香坊店）", "香坊区", "香坊大街 12 号", "香坊", 2, "老影院改造，票价便宜"),
    ("钱柜 KTV（果戈里店）", "南岗区", "果戈里大街 66 号", "果戈里", 3, "量贩式，含自助小食"),
    ("温莎 KTV（中央大街店）", "道里区", "中央大街 100 号", "中央大街", 3, "中包可坐 8 人"),
    ("纯 K（索菲亚店）", "道里区", "透笼街 12 号", "中央大街", 3, "量贩式 KTV，工作日晚场 5 折"),
    ("木北造型（哈西店）", "南岗区", "哈西万达广场 2 层", "哈西", 4, "剪发和造型，需预约"),
    ("东田造型（中央大街店）", "道里区", "中央大街 66 号", "中央大街", 4, "剪发不含洗，需另付"),
    ("丝域养发（学府路店）", "南岗区", "学府路 20 号", "哈工大", 4, "头皮护理和养发套餐"),
    ("乐刻运动（哈西店）", "南岗区", "哈西万达广场 5 层", "哈西", 5, "24 小时健身房，月付制"),
    ("超级猩猩健身（友谊路店）", "道里区", "友谊路 55 号", "中央大街", 5, "团操课为主，单次约课"),
    ("一兆韦德（群力店）", "道里区", "群力远大购物中心 6 层", "群力", 5, "器械区和泳池"),
]


def jitter(anchor):
    dx, dy = ANCHORS[anchor]
    return round(dx + random.uniform(-0.0012, 0.0012), 6), round(dy + random.uniform(-0.0012, 0.0012), 6)


def gen_shops(start_id=11):
    shops = []
    for i, (name, area, address, anchor, tid, tagline) in enumerate(REAL_SHOPS):
        x, y = jitter(anchor)
        score = int(random.gauss(44, 3))
        score = max(35, min(50, score))
        # 营业时间要和「特色」描述自洽，否则知识库里会出现
        # “08:00 开门”和“开到凌晨”并存的矛盾，RAG 会答错
        hours = random.choice(TYPE_HOURS[tid])
        if tid == 1:
            if "凌晨" in tagline or "夜宵" in tagline:
                hours = "16:00-次日02:00"
            elif "早点" in tagline or "上午" in tagline:
                hours = "06:30-14:00"

        shops.append(dict(
            id=start_id + i, name=name, type_id=tid, type_name=TYPE_NAME[tid],
            district=area, region=anchor, address=address, x=x, y=y,
            avg_price=random.randint(*TYPE_PRICE[tid]), hours=hours,
            score=score, sold=random.randint(80, 2600), comments=random.randint(20, 600),
            tagline=tagline,
        ))
    return shops


def gen_vouchers(shops, start_id=3):
    vouchers, seckill = [], []
    vid = start_id
    now = datetime(2026, 11, 10, 11, 0)
    for shop in shops:
        if random.random() > 0.45:
            continue
        is_seckill = random.random() < 0.06
        if is_seckill:
            title = f"{shop['name']} 限时秒杀券"
            pay, actual, vtype = 1000, 5000, 1
        else:
            discount = random.choice([20, 30, 50])
            title = f"{shop['name']} {discount} 元代金券"
            pay, actual, vtype = 1000, discount * 500, 0
        vouchers.append(dict(
            id=vid, shop_id=shop["id"], title=title,
            sub_title="到店核销，不与其他优惠同享",
            rules="仅限堂食；节假日不可用；每单限用一张",
            pay_value=pay, actual_value=actual, type=vtype, status=1,
        ))
        if is_seckill:
            seckill.append(dict(
                voucher_id=vid, stock=random.choice([50, 100, 200]),
                begin_time=(now - timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S"),
                end_time=(now + timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S"),
            ))
        vid += 1
    return vouchers, seckill


def gen_blogs(shops, start_id=5, count=120):
    blogs = []
    for i in range(count):
        s = random.choice(shops)
        blogs.append(dict(
            id=start_id + i, shop_id=s["id"], user_id=random.choice([1, 2]),
            title=f"{s['name']}打卡：{random.choice(['人均不高', '服务不错', '环境安静', '性价比可以', '周末人多'])}",
            content=f"{s['tagline']}。{random.choice(['下次还会来', '适合聚餐', '建议提前预约', '停车不太方便'])}。",
        ))
    return blogs


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "''")


def write_sql(shops, vouchers, seckill, blogs):
    path = os.path.join(ROOT, "sql", "seed_shops.sql")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    L = []
    L.append("-- ============================================================")
    L.append("-- 自动生成，请勿手工修改。重新生成：python tools/gen_seed.py")
    L.append("--")
    L.append("-- 商户名称与地址来源：哈尔滨市商务局 2024-02 发布的美食地图")
    L.append("--   https://www.hrbtv.net/folder137/2024-02-15/877233.html")
    L.append("-- 营业时间/人均/评分/经纬度为演示用估计值，不代表真实经营数据。")
    L.append(f"-- 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    L.append(f"-- 商户 {len(shops)} / 券 {len(vouchers)}（秒杀 {len(seckill)}） / 笔记 {len(blogs)}")
    L.append("-- ============================================================")
    L.append("")
    L.append("-- 可重复执行：先清掉生成数据（不动 init.sql 的种子 1-10）")
    L.append("DELETE FROM tb_seckill_voucher WHERE voucher_id >= 3;")
    L.append("DELETE FROM tb_voucher WHERE id >= 3;")
    L.append("DELETE FROM tb_blog WHERE id >= 5;")
    L.append("DELETE FROM tb_shop WHERE id >= 11;")
    L.append("")
    L.append("INSERT INTO tb_shop (id,name,type_id,images,area,address,x,y,avg_price,sold,comments,score,open_hours) VALUES")
    rows = []
    for s in shops:
        rows.append(
            f"({s['id']},'{esc(s['name'])}',{s['type_id']},'',"
            f"'{esc(s['region'])}','{esc(s['district'] + s['address'])}',"
            f"{s['x']},{s['y']},{s['avg_price']},{s['sold']},{s['comments']},{s['score']},'{s['hours']}')"
        )
    L.append(",\n".join(rows) + ";")
    L.append("")
    if vouchers:
        L.append("INSERT INTO tb_voucher (id,shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES")
        L.append(",\n".join(
            f"({v['id']},{v['shop_id']},'{esc(v['title'])}','{esc(v['sub_title'])}',"
            f"'{esc(v['rules'])}',{v['pay_value']},{v['actual_value']},{v['type']},{v['status']})"
            for v in vouchers) + ";")
        L.append("")
    if seckill:
        L.append("INSERT INTO tb_seckill_voucher (voucher_id,stock,begin_time,end_time) VALUES")
        L.append(",\n".join(
            f"({s['voucher_id']},{s['stock']},'{s['begin_time']}','{s['end_time']}')" for s in seckill) + ";")
        L.append("")
    if blogs:
        L.append("INSERT INTO tb_blog (id,shop_id,user_id,title,content) VALUES")
        L.append(",\n".join(
            f"({b['id']},{b['shop_id']},{b['user_id']},'{esc(b['title'])}','{esc(b['content'])}')" for b in blogs) + ";")
        L.append("")
    L.append("SELECT COUNT(*) AS shops FROM tb_shop;")
    L.append("SELECT COUNT(*) AS vouchers FROM tb_voucher;")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L))
    return path


def write_knowledge(shops):
    content_dir = os.path.join(AGENT_ROOT, "src", "main", "resources", "content")
    os.makedirs(content_dir, exist_ok=True)
    for old in os.listdir(content_dir):
        if old.startswith("shops-"):
            os.remove(os.path.join(content_dir, old))

    grouped = {}
    for s in shops:
        grouped.setdefault(s["region"], []).append(s)

    paths = []
    for region, items in grouped.items():
        lines = [f"# {region}商圈商户名录\n"]
        for s in items:
            lines.append(f"## {s['name']}")
            lines.append(f"分类：{s['type_name']} ｜ 商圈：{s['region']} ｜ 城区：{s['district']}")
            lines.append(f"地址：{s['address']}")
            lines.append(f"营业时间：{s['hours']}")
            lines.append(f"人均：{s['avg_price'] // 100} 元 ｜ 评分：{s['score'] / 10:.1f} ｜ 已售：{s['sold']}")
            lines.append(f"特色：{s['tagline']}")
            lines.append("")
        path = os.path.join(content_dir, f"shops-{region}.md")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines))
        paths.append(path)
    return paths


def write_eval(shops, count=50):
    picked = random.sample(shops, min(count, len(shops)))
    cases = []
    for i, s in enumerate(picked):
        k = i % 6
        if k == 0:
            cases.append({"q": f"{s['name']}几点开门", "expect": s["hours"].split("-")[0]})
        elif k == 1:
            cases.append({"q": f"{s['name']}人均多少钱", "expect": str(s["avg_price"] // 100)})
        elif k == 2:
            cases.append({"q": f"{s['name']}在哪条街", "expect": s["address"][:4]})
        elif k == 3:
            cases.append({"q": f"{s['name']}是什么分类", "expect": s["type_name"]})
        elif k == 4:
            cases.append({"q": f"{s['name']}评分多少", "expect": f"{s['score'] / 10:.1f}"})
        else:
            cases.append({"q": f"{s['region']}有哪些烧烤店", "expect": "烧烤"})

    path = os.path.join(AGENT_ROOT, "src", "main", "resources", "eval", "questions.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(cases, f, ensure_ascii=False, indent=2)
    return path


def main():
    shops = gen_shops()
    vouchers, seckill = gen_vouchers(shops)
    blogs = gen_blogs(shops)
    sql = write_sql(shops, vouchers, seckill, blogs)
    kb = write_knowledge(shops)
    ev = write_eval(shops)
    print(f"商户   {len(shops)}")
    print(f"券     {len(vouchers)}（秒杀 {len(seckill)}）")
    print(f"笔记   {len(blogs)}")
    print(f"SQL    {sql}")
    print(f"知识库 {len(kb)} 个文件")
    print(f"评测集 {ev}")


if __name__ == "__main__":
    main()
