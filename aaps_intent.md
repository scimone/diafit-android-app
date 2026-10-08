
via broadcast intents

carbs, bolus, smb, temp basal rate

```logs
2024-06-29 18:40:54.764 16713-16713 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-29 18:40:54.765 16713-16713 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-29 18:40:54.765 16713-16713 NSClientReceiver        scimone.diafit                       I  Treatment JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-29T16:40:29.000Z","isValid":true,"date":1719679229000,"_id":"66803902fe3f79032a517960"},{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-29T16:40:29.000Z","isValid":true,"date":1719679229000,"_id":"66803902fe3f79032a517960"}]
2024-06-29 18:41:35.140 16713-16713 CGMReceiver             scimone.diafit                       I  Received glucose: 114.0
2024-06-29 18:41:35.142 16713-16713 MainActivity            scimone.diafit                       D  New glucose value received: 114.0
2024-06-29 18:41:35.155 16713-16713 CGMReceiver             scimone.diafit                       I  Received glucose: 114.0
2024-06-29 18:41:39.174 16713-16713 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-29 18:41:39.175 16713-16713 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-29 18:41:39.175 16713-16713 NSClientReceiver        scimone.diafit                       I  Treatment JSON: [{"eventType":"Carb Correction","carbs":2,"notes":"","created_at":"2024-06-29T16:41:29.000Z","isValid":true,"date":1719679289000}]
```

```
2024-06-30 19:21:52.256 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:21:52.257 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.1,"created_at":"2024-06-30T17:21:41.977Z","date":1719768101977,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528174,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241"}]
2024-06-30 19:22:02.505 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:22:02.505 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:22:02.506 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.1,"created_at":"2024-06-30T17:21:41.977Z","date":1719768101977,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528174,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"66819426fe3f79032a5180e2"}]
```

```
Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:24:21.407Z","isValid":true,"date":1719768261407,"_id":"668194c5fe3f79032a5180e8"},{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:24:21.407Z","isValid":true,"date":1719768261407,"_id":"668194c5fe3f79032a5180e8"}]
```

calculator:
```
2024-06-30 19:26:29.441 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:26:29.442 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.05,"created_at":"2024-06-30T17:26:08.658Z","date":1719768368658,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528179,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"66819531fe3f79032a5180ee"},{"eventType":"Meal Bolus","insulin":0.05,"created_at":"2024-06-30T17:26:08.658Z","date":1719768368658,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528179,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"66819531fe3f79032a5180ee"}]
2024-06-30 19:26:29.448 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:26:29.449 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:26:29.449 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:26:07.319Z","isValid":true,"date":1719768367319}]
```

# bolus:
Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:41:36.747 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:41:36.747 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.1,"created_at":"2024-06-30T17:41:26.418Z","date":1719769286418,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528190,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241"}]
2024-06-30 19:41:47.056 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:41:47.056 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:41:47.056 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.1,"created_at":"2024-06-30T17:41:26.418Z","date":1719769286418,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528190,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"668198c6fe3f79032a518108"}]


# carbs:
Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:42:43.181 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:42:43.181 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:42:23.000Z","isValid":true,"date":1719769343000}]
2024-06-30 19:42:53.434 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:42:53.436 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:42:53.437 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:42:23.000Z","isValid":true,"date":1719769343000,"_id":"66819909fe3f79032a518109"}]

# calculator:
Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:44:53.505 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:44:53.506 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Meal Bolus","insulin":0.05,"created_at":"2024-06-30T17:44:32.777Z","date":1719769472777,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528192,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"66819981fe3f79032a51810e"},{"eventType":"Meal Bolus","insulin":0.05,"created_at":"2024-06-30T17:44:32.777Z","date":1719769472777,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112528192,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","_id":"66819981fe3f79032a51810e"}]
2024-06-30 19:44:53.509 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:44:53.509 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:44:53.510 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:44:27.025Z","isValid":true,"date":1719769467025}]
2024-06-30 19:45:08.859 25553-25553 NSClientReceiver        scimone.diafit                       I  Received nsclient action: info.nightscout.client.NEW_FOOD
2024-06-30 19:45:08.860 25553-25553 NSClientReceiver        scimone.diafit                       I  Received new treatment or food
2024-06-30 19:45:08.860 25553-25553 NSClientReceiver        scimone.diafit                       I  Treatments JSON: [{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-30T17:44:27.025Z","isValid":true,"date":1719769467025,"_id":"66819990fe3f79032a51810f"}]