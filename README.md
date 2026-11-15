# konomitv-tv-app — KonomiTV 用 Android TV ラッパー

[KonomiTV](https://github.com/tsukumijima/KonomiTV) を Android TV で全画面表示
するための、薄い WebView ラッパーアプリ。ブラウザの UI を介さず、テレビの
ランチャーから直接 KonomiTV を開けるようにする。

操作系は KonomiTV 側の D-pad 空間ナビゲーションに任せる前提で、このアプリは
キーイベントを WebView へ素通しする。空間ナビなどのクライアント側改造は
[konomitv-customizations](https://github.com/tatsuya0902/konomitv-customizations)
の tv-support パッチ側にある (このアプリ単体でも素の KonomiTV は表示できる)。

## 機能

- 起動直後から没入型フルスクリーン (システム UI を隠し、画面スリープを抑止)
- User-Agent に `KonomiTV-TV` の印を付与 (KonomiTV 側がテレビ向けラッパーだと
  判別して、空間ナビや全画面の扱いを切り替えるための印)
- リモコンのメディアキー対応: 再生／一時停止／停止／早送り／巻き戻し (±60 秒)／
  前／次。前／次はまずページ側のチャプター移動 (`window.__konomiChapterSeek`、
  CM 区間の境目) を試し、無ければ「前の話／次の話」ボタンのクリックに落とす
- 戻るキーの 2 段階動作: 1 回目はページへ Escape を送る (全画面の終了・
  ダイアログを閉じる等)、2 秒以内の 2 回目で実際に前の画面へ戻る
- HTML5 フルスクリーン (プレイヤーの全画面ボタン) に対応

## 動作環境

- Android TV / Google TV (minSdk 24、targetSdk 29)
- 作者環境: Sony Bravia KJ-43X8500G (Android 10) で実機確認
- 表示先の KonomiTV サーバーが LAN 内にあること

## ビルド

Gradle を使わない手作業ビルド。JDK 17 と Android SDK (platform android-29、
build-tools 34.0.0) が `$HOME/jdk17` と `$HOME/android-sdk` にある前提で、
場所が違う場合は build.sh の冒頭を書き換える。

```sh
bash build.sh   # konomitv-tv.apk ができる (署名用 debug.keystore は自動生成)
```

## インストール

```sh
adb install -r konomitv-tv.apk
```

## 開く URL の変更

既定では作者宅の KonomiTV (`https://192-168-100-6.local.konomi.tv:7000/`) を
開く。サーバーの場所が違う場合は、次のどちらかで変更できる。

- MainActivity.java の `HOME_URL` を書き換えてビルドし直す
- 一度だけ Intent で渡す (以後はその URL が保存される):

```sh
adb shell am start -n jp.tatsuya0902.konomitv.tv/.MainActivity -e url https://<あなたのホスト>:7000/
```

なお KonomiTV は `*.local.konomi.tv` のホスト名 (SNI) が必要な構成のため、
IP アドレス直打ちでは表示できない点に注意。

## ライセンス

MIT License (LICENSE を参照)。
