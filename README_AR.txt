PESS V36.2 — حزمة إضافة المساعد الذكي

محتويات الحزمة:
- index.html: واجهة V36.1 مع عنوان API يعمل من Android WebView ومن الموقع.
- netlify/functions/pess-assistant.mjs: وظيفة الخادم التي تتصل بـ Netlify AI Gateway عبر OpenAI SDK.
- package.json: اعتماد مكتبة OpenAI.
- netlify.toml: إعداد نشر Netlify Functions.

طريقة النشر:
1) فك ضغط الحزمة.
2) ارفع محتويات المجلد إلى جذر مستودع GitHub PESS-INVOICE- مع استبدال index.html وإضافة الملفات والمجلدات الجديدة.
3) نفّذ Commit changes.
4) انتظر Deploy على Netlify حتى يصبح Published/Ready.
5) تأكد من تفعيل AI Features / AI Gateway في حساب Netlify؛ يلزم نشر إنتاجي واحد على الأقل لتفعيل البوابة.
6) اختبر من الموقع ومن تطبيق Android.

لا تضع أي API key داخل index.html أو ملفات العميل. وظيفة الخادم تستخدم بيانات الاعتماد التي يحقنها Netlify AI Gateway وقت التشغيل.
