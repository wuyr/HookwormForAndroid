## 一个基于Magisk&Riru的Module，可以助你用超低成本开发各种Hook插件，无须Xposed

>### 此Module仅供大家学习研究，请勿用于商业用途。利用此Module进行非法行为造成的一切后果自负！

### 博客详情： 敬请期待。。。

### 背景：
前段时间在WanAndroid每日一问里有个问题：**"应用进程中那4个Binder线程分别是跟谁通讯？"** 

一番简单分析无果后，就想着写个Xposed插件来hook Thread对象的创建，但是看到有同学提醒：**"Xposed插件的`handleLoadPackage`方法是在`handleBindApplication`时才回调的！"**。 没办法，只能找其他的方案了。

忽然想到了Magisk，但是我又不会做Magisk插件…… 

第二天看了下自己一直在用的那个【开启微信指纹支付】的Magisk模块源码（其实之前也看过好多遍了，一直没看懂，一头雾水），发现核心代码其实就是libs下面的那个APK的dex！ 

反编译看了下，大概摸清了思路，但是想到用这种方式（先手动打包成apk放在插件项目libs下）开发起来太繁琐了，而且维护起来成本又高，还没有一个规范的模板，这样就很难抽出来为自己所用。

于是心有不甘的我又继续在github上面搜Riru相关的模块，看到一个叫【QQ Simplify】的项目，是用来阉割QQ一些 “花里胡哨” 的功能的，看了下代码，它是在进程Fork之后，用反射把ServiceFetcher里面的LayoutInflater对象换成自己的代理类，这样就可以在布局inflate时，选择性地把一些View的宽高set为0，达到隐藏的效果。

结合这两个项目的部分代码以及思路，我封装出来一个入侵程度非常低的Module，开发新的插件的话，只需要添加这个Module的依赖，然后在`module.properties`中配置一下模块属性就行了，非常简单！

<br/>

### 原理：
有一个叫Riru的Magisk模块，它会把系统的libmemtrack.so替换掉，并对外公开Zygote初始化进程的一些API，比如nativeForkAndSpecialize。 

在Zygote Fork进程的前后，都会对外 “发通知” ，如果趁这个时机向指定进程注入自己的代码，那么，当进程启动完成后，自己的代码就运行在指定进程内了，这样就可以~~为所欲为~~扩展一些功能，或者更改某些逻辑等等。

<br/>

### Q&A：
**这个Module能做什么？**

要知道，你的代码是运行在目标进程中的，这就相当于你参与了目标app的开发！

所以理论上目标进程中的所有数据以及行为，都能修改成你想要的结果，只要你能拿到对应的对象。

至于怎样拿到对象，这就看具体场景了。

比如你想修改某个app的某个页面按钮点击行为，那就可以先监听目标Activity的生命周期来获取到对应的Activity对象，进一步find到View实例然后给它重新set一个OnClickerListener。

再比如你想修改某个app的启动图，一个比较通用的方法是：监听对应Activity的onCreate，在这里把`Activity.mWindow`的`mContentParent`替换成你自定义的FrameLayout，这样你就能在`onLayout`之前找到显示启动图的View实例，并对它做手脚。

**它跟Xposed的关系/区别？**

可以说是完全无关系的。

从能力上来看，很明显Xposed更强大，不过相对的，Xposed插件开发起来难度也会高一些，因为它的优势主要体现在能监听任何一个方法调用，这就非常考验你对目标app代码的熟悉程度了，如果没掌握一定的逆向知识是搞不来的。

反观这个Module，它的能力是不如Xposed的，比如它不能监听哪些Class被加载，不能直接感知到哪些对象被创建。只能用一些比较原始的方法来修改数据和行为，比如反射，动态代理等。优势是开发成本很低，甚至你不用反编译目标app，没有逆向基础也可以，只需要一个*UIAutomatorViewer*工具来帮助获取到布局结构和资源id就能着手开发了。还有就是，目标app很难感知到这个插件的存在，它不像Xposed在异常堆栈中能看到相关字眼。无论安装和运行，都可以说是不留痕迹的。

<br/>

### 亮点：
 - 配置成本极低，添加这个Module依赖就行了，你只需关注你自己的代码逻辑；
 
 - 所有的配置都集中到了assets/module.properties文件里，直接修改这个文件即可，比如主入口类，目标进程等；
 
 - 提供了一些基本的API，方便进行Hook工作，一般情况下，只需要几行代码就能监听到按钮的点击，或者布局的加载了；
 
 - **自动刷入！** 是的，从此解放双手，像开发普通应用那样，编译完就能自动刷入手机了，如果是手动安装的话，每次至少浪费20秒时间；

<br/>

### 使用：
 1. 首先，clone或直接下载这个Module（注意！这只是一个Module，需要被依赖到一个APP Module才能正常运作）；
 
 2. 新建一个Android项目，Minimum SDK至少为23（即6.0）。为了避免不必要的麻烦，Language请选择Kotlin而不是默认的Java，因为这个Module用到了Kotlin；
 
 3. 新建好项目后，创建一个入口类，名字随便，比如就叫ModuleMain。然后，在里面声明一个 ***public static void main(String packageName)*** 方法，这个方法是必须有的！当插件启动后会被调用；

 4. 导入刚刚下载Module，并依赖到主模块中；
 
 5. 配置插件属性，修改Module/src/main/assets下的module.properties，比如给moduleMainClass属性填上刚刚创建的ModuleMain完整类名（带包名）；
 
<br/>
 
**module.properties属性如下：**

 - **moduleId**：模块唯一标识，只能使用字母 + 下划线组合，如：my_module_id；
 
 - **moduleName**：模块名称，自由填写；
 
 - **moduleAuthor**：模块作者，自由填写；
 
 - **moduleDescription**：模块描述，自由填写；
 
 - **moduleVersion**：版本号，自由填写；
 
 - **moduleMainClass**：主入口类名，例：com.demo.ModuleMain；
 
 - **targetProcessName**：目标进程名/包名，即要寄生的目标。不填写则寄生所有进程。同时寄生多个目标，用  ;  分隔，如: com.demo.application1;com.demo.application2；
 
 - **automaticInstallation**：编译完毕自动安装模块（需要手机已通过adb连接到电脑（只能连一台），并已安装Magisk和Riru模块！）。1为开启，其他值为关闭；

配置好这些属性之后，就可以编译打包运行了！
注意，编译打包的话，需要运行***project:assemble***这个Task，不是***app:assemble***也不是***module:assemble***！
    
<br/>

### 常用API：

|Name|Description|
|------|-----------|
|Hookworm.setOnApplicationInitializedListener|监听Application初始化|
|Hookworm.registerActivityLifecycleCallbacks|监听Activity的生命周期|
|Hookworm.registerPreInflateListener|在LayoutInflater加载布局前做手脚|
|Hookworm.registerPostInflateListener|在LayoutInflater加载布局后做手脚|
|Hookworm.getApplication|获取进程Application实例|
|Hookworm.getActivities|获取进程存活Activity实例集合|
|Hookworm.findActivityByClassName|根据完整类名查找Activity对象|
|HookwormExtensions.findViewByIDName|根据资源id名来查找View实例|
|HookwormExtensions.findAllViewsByIDName|根据资源id名来查找所有对应的View实例|
|HookwormExtensions.findViewByText|根据显示的文本来查找View实例|
|HookwormExtensions.findAllViewsByText|根据显示的文本来查找所有对应的View实例|
|HookwormExtensions.containsText|检测目标View是否包含某些文本|

<br/>

### 功能示例Demo： 敬请期待。。。

<br/>

### 感谢：
首先感谢鸿神和[WanAndroid每日一问](https://wanandroid.com/wenda)，如果没有那天的那个问题就没有这个库。

感谢[Fingerprint pay for WeChat](https://github.com/eritpchy/Fingerprint-pay-magisk-wechat)和[QQ Simplify](https://github.com/Kr328/Riru-QQSimplify)，从这两个项目中学到很多思路以及代码。。。

感谢[Riru](https://github.com/RikkaApps/Riru)和[Magisk](https://github.com/topjohnwu/Magisk)，这两个东西是此Module的根基。

最后感谢大家的小星星🌟🌟，虽然可能屏幕前的你还没有点，但是先谢谢了！
