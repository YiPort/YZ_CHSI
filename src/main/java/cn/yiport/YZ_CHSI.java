package cn.yiport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YZ_CHSI {
    static List<Integer> colIndex = Arrays.asList(0,4,5,6,8,9);
    static List<HashMap<String, String>> professions = new ArrayList<HashMap<String, String>>(){};

    static String cities = null;

    public static void main(String[] args) throws Exception {


        //自动挡
//        HashMap<String, String> zyMap = new HashMap<>();
//        zyMap.put("mc", "电子信息");//专业名称
//        zyMap.put("dm", "0854");
//        professions.add( zyMap);
//        xxfs 学习方式(1全日制2非全日制)
//        String xxfs ="1";
        //注意linux和windos文件目录格式
//        String location="E:\\Users\\Administrator\\Desktop\\考研";


        //手动档
        Scanner scanner=new Scanner(System.in);
        System.out.println("小叶的规则：\n请先在  https://yz.chsi.com.cn/zyk/  查阅专业名称和代码，其他按要求填写即可");
        String  flag="n";
        while (!flag.equals("y")&& !flag.equals("Y")) {
            System.out.println("请输入专业名称(例如：电子信息):");
            String mc = scanner.next();
            HashMap<String, String> zyMap = new HashMap<>();
            zyMap.put("mc", mc);//专业名称
            System.out.println("请输入学科代码(例如：电子信息-0854，则输入0854):");
            String dm = scanner.next();
            zyMap.put("dm", dm);//学科代码
            professions.add( zyMap);
            System.out.println("是否退出，输入“y”退出,进入下一步");
            flag=scanner.next();
        }
        System.out.println("请输入学习方式(1全日制2非全日制，输入其他则默认全日制):");
        String xxfs = scanner.next();
        System.out.println("请输入文件保存路径（注意路径文件夹不能有空格）:");
        String location=scanner.next();


        cities = Jsoup.connect("https://yz.chsi.com.cn/zsml/pages/getSs.jsp").execute().body();
        List<MyThread> threadList = new ArrayList<>();
        for (HashMap<String, String> profession : professions) {
            String dms = profession.get("dm");
            String mcs = profession.get("mc");
            threadList.add(new MyThread(dms, mcs,xxfs,location));
        }
        ExecutorService executor = Executors.newFixedThreadPool(threadList.size());
        for (MyThread thread : threadList) {
            executor.execute(() -> {
                System.out.println("小叶：开始爬取研招网-" + thread.mc+thread.xxfsName+ thread.getName());
                thread.run();
                System.out.println("小叶：爬取研招网完成-" + thread.dm+thread.xxfsName+ thread.getName());
            });
        }

        executor.shutdown();
    }

    static class MyThread extends Thread {
        String dm;
        String mc;
        String xxfs;
        FileWriter fo;
        String xxfsName;

        MyThread(String dm, String mc,String xxfs,String location) throws IOException {
            this.dm = dm;
            this.mc = mc;
            this.xxfs=xxfs;
            //学习方式名称
            this.xxfsName="";


           if(xxfs.equals("2")){
                this.xxfsName="非全日制";
            }else{
                this.xxfs="1";
                this.xxfsName="全日制";
            }
            System.out.println("------------"+xxfsName);
            String fileName=location+"\\"+ new SimpleDateFormat("yyyy年MM月-").format(new Date()) + mc + xxfsName+"招生学校和专业清单.md";
            System.out.println("小叶提示你：输出文件路径为："+fileName);
            this.fo = new FileWriter(fileName);
        }

        @Override
        public void run() {
            JsonParser parser = new JsonParser();
            JsonArray citiesArr = parser.parse(cities).getAsJsonArray();
            for (JsonElement city : citiesArr) {
                JsonObject cityObj = city.getAsJsonObject();
                String cityName = cityObj.get("mc").getAsString();
                try {
                    fo.write("# " + cityName + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                getSchool(cityObj.get("dm").getAsString(), dm,xxfs);
            }
            try {
                fo.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void getSchool(String ssdm, String dm,String xxfs) {
            try {
                // ssdm 省市代码 yjxkdm 学科代码  zymc 专业名称,xxfs 学习方式(1全日制2非全日制)
                Connection conn = Jsoup.connect("https://yz.chsi.com.cn/zsml/queryAction.do")
                        .data("ssdm", ssdm)
                        .data("yjxkdm", dm)
                        .data("xxfs", xxfs)
                        .method(Connection.Method.POST);
                Document doc = conn.get();
                Elements items = doc.select(".ch-table a");
                for (Element item : items) {
                    try {
                        fo.write("## " + item.text() + "\n");
                        fo.write("| 院系所   |  专业  |  研究方向  |   考试范围 |  \n");
                        fo.write("| - | - | - |  - |   \n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    getProfession(item.attr("href"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void getProfession(String url) {
            try {
                Connection.Response professionHtml = Jsoup.connect("https://yz.chsi.com.cn" + url).method(Connection.Method.POST).execute();
                Document professionDoc = professionHtml.parse();
                Elements trs = professionDoc.select(".more-content tr");
                for (Element trItem : trs) {
                    handleTd(trItem);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleTd(Element trItem) throws IOException {
            for (int index = 0; index < trItem.select("td").size(); index++) {
                if (colIndex.contains(index)) {
                    continue;
                }
                Element tdItem = trItem.select("td").get(index);
                if (index == 7) {
                    this.fo.write("| " + "[详情](https://yz.chsi.com.cn" + tdItem.select("a").get(0).attr("href") + ") |\n");
                    continue;
                }
                String value = tdItem.text().isEmpty() ? " " : tdItem.text();
                this.fo.write(" | " + value);
            }
        }
    }


}
