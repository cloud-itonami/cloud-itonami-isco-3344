# physai-isco-3344 — 医療秘書（ISCO 3344）の予約・ファイリング調整ロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-3344`、ISCO 3344 医療秘書）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 医療事務の予約・ファイリングロボットが予約調整・カルテ綴じ込み状況の記録・備品発注の調整を行う（臨床内容は扱わない）。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:chart-cart-to-records-room` | transport | カルテ綴じ込み用の台車を受付から記録室まで 45 m 運ぶ | 1 区間の所要時間 | 70 s（estimate） |
| `:supply-box-to-shelf` | manipulator | 届いた事務用品の箱を配送台車から備品棚へ持ち上げる | 肩関節ピークトルク | 75 N·m（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:test`（`test/medsecretary/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
この repo 自身の `.cljk` test も kbb で一緒に走る。test 数はそれらと physics の test の合計。

## 測って分かったこと・限界（成長の第一候補）

1. **カルテ台車**: 所要時間は積荷 10〜60 kg で 51.77 s のまま（加速度上限 0.4 m/s² と速度上限 0.9 m/s が効く）、100 kg で駆動力制限に入り 51.85 s、150 kg で 52.65 s。
   限界 70 s を超えるのは **積荷 324.7 kg から**。積荷で変わるのはエネルギー（456 J → 1733 J）。転倒余裕 0.834。
2. **備品箱**: 肩トルクは 1 kg で 29.78 N·m、5 kg で 52.97 N·m、7.5 kg で 67.53 N·m、10 kg で 82.10 N·m（限界超過）。限界 75 N·m に達するのは **8.782 kg**。
3. **estimate のままの値**: 1 区間 70 s（受付の来院間隔の実測で置き換える）、肩トルク上限 75 N·m（協働アームの仕様書で置き換える）、台車の駆動力・転がり抵抗係数、アームの寸法・質量。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-3344 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-3344 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
