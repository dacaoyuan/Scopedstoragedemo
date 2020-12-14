## 说明：此源码是根据 [郭霖](https://github.com/guolindev/ScopedStorageDemo) 代码基础上的完善和修改

**1:在此基础上新增了相册图片的删除，以及卸载app后，再去删除图片的解决方案。** 

知识补充：
     * 开了沙箱之后，之前的媒体库生成的文件在其记录上会打上owner_package的标志，标记这条记录是你的app生成的。
     * 当你的app卸载后，MediaStore就会将之前的记录去除owner_package标志，
     * 也就是说app卸载后你之前创建的那个文件与你的app无关了（不能证明是你的app创建的）。
     * 所以当你再次安装app去操作之前的文件时，媒体库会认为这条数据不是你这个新app生成的，所以无权删除或更改。
     * 处理方案：
     * 采用此种方法，删除相册图片，会抛出SecurityException异常，捕获后做下面的处理，会出现系统弹框，提示你是否授权删除。
     * 点击授权后，我们在onActivityResult回调中，再次做删除处理，理论上就能删除。


郭老师文章参考：

[中文讲解](https://guolin.blog.csdn.net/article/details/105419420)
