package com.f5.ourfarm.activity;

import static com.f5.ourfarm.util.URLConstants.GET_AROUND_FARM_HOME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.f5.ourfarm.R;
import com.f5.ourfarm.layout.PullToRefreshView;
import com.f5.ourfarm.layout.PullToRefreshView.OnFooterRefreshListener;
import com.f5.ourfarm.model.LoadWay;
import com.f5.ourfarm.model.MoreAroundFarm;
import com.f5.ourfarm.model.Summary;
import com.f5.ourfarm.util.Constants;
import com.f5.ourfarm.util.HttpUtil;
import com.f5.ourfarm.util.Tools;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.umeng.analytics.MobclickAgent;

/**
 * @author lify
 */
public class AroundFarmActivity extends Activity implements OnFooterRefreshListener{
    OnClickListener lback = null;
    OnClickListener details = null;
    
    //loadingbar
    private LinearLayout loadingLayout;
    //概要信息
    private LinearLayout summaryLayout;
    //上拉刷新
    PullToRefreshView mPullToRefreshView;
    
    //景点周边的农家乐
    private List<MoreAroundFarm> farmList = new ArrayList<MoreAroundFarm>();
    
    //记录第几次请求，每请求一次，返回10条记录
    private int queryCounts = 1;
    
  	private static String TAG = "周边农家乐页面";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 
        // 去除标题栏
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //准备listeners
        this.prepareListeners();
        setContentView(R.layout.activity_around_farm);
        
        initStatus();
        
        //设置listeners
        this.batchSetListeners();
    }
    
    /**
     * 初始页面时的控件状态设定
     */
    private void initStatus() {
        mPullToRefreshView = (PullToRefreshView) findViewById(R.id.main_pull_refresh_view);
        //不能上拉页面
        mPullToRefreshView.lock();
        
        //loadingbar
        loadingLayout = (LinearLayout)findViewById(R.id.nearby_loadingbar);
        //概要信息
        summaryLayout = (LinearLayout)findViewById(R.id.ListView_main);
        
        //设置显示情况
        loadingLayout.setVisibility(View.VISIBLE);
        summaryLayout.setVisibility(View.GONE);
        
        //获取请求到的周边农家乐
        farmList = (List<MoreAroundFarm>)getIntent().getSerializableExtra(Constants.AROUND_FARM);
        if(farmList != null && farmList.size() > 0) {
        	//初始化，表示请求是第2次
            queryCounts = 2;
        	showList(farmList);
        	requestDataSuccess();
        } else {
        	queryCounts = 1;
        	new Thread(new RunnableImp(LoadWay.INIT_LOAD)).start();
        }
    }
    
    public void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }
    public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }
    
    /**
     * 监听到事件后的动作；
     */
    private void prepareListeners() {
        //nearby->home
        lback = new OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        };
        //details
        details = new OnClickListener(){
            public void onClick(View v) {
            	// 判断网络是否可用，若不可用则提示用户，并不进行跳转
            	if(!Tools.checkNetworkStatus(AroundFarmActivity.this)) {
            		return;
            	}
                long destinationId =  (Long) v.getTag(); 
                Log.i("res", ""+destinationId);
                Intent nearby2detail = new Intent(AroundFarmActivity.this,DetailActivity.class);
                //将服务器返回数据保存入Intent，确保数据可在activity间传递
                nearby2detail.putExtra("destinationId", destinationId);
                //用于判断点击的是那种类型
                nearby2detail.putExtra("nearbyType", 2);
                
                startActivity(nearby2detail);
            } 
        };
    }
    
    /**
     * 绑定view和监听
     */
    private void batchSetListeners() {
        // back
        ImageView iback2home = (ImageView) this.findViewById(R.id.ImageView_button_back);
        iback2home.setOnClickListener(lback);
        
        mPullToRefreshView.setOnFooterRefreshListener(this);
    }
    
    
    /**
     * 构造附近列表
     * 根据查询参数查找附近内容，然后将内容概要信息显示出来。
     */
    private void showList(List<MoreAroundFarm> aroundFarm) {
        LayoutInflater flater = LayoutInflater.from(this);
        LinearLayout list = (LinearLayout) this.findViewById(R.id.ListView_main);
//        list.removeAllViews();
        
        // 获取查询结果，循环构造概要列表
        for(MoreAroundFarm maf: aroundFarm){
            Long destinationId = maf.getView_id();
            Summary ds = maf.getSummary();
            View v = flater.inflate(R.layout.listview_child_nearby, null);
            // pic
            ImageView ivPic = (ImageView) v.findViewById(R.id.ImageView_destination_pic);
            Bitmap bm = Tools.getBitmapFromUrl(ds.getPic());
            if(bm != null){
                ivPic.setImageBitmap(bm);
            }
            // name
            TextView tvName = (TextView) v
                    .findViewById(R.id.ListView_destination_name);
            tvName.setText(ds.getName());
            // sroce
            RatingBar rbSroce = (RatingBar) v
                    .findViewById(R.id.ratingBar_destination_sroce);
            rbSroce.setRating(ds.getScore());
            // price info
            TextView tvPrice = (TextView) v
                    .findViewById(R.id.ListView_destination_price);
            tvPrice.setText(ds.getPriceInfo());
            // hot
            TextView tvHot = (TextView) v
                    .findViewById(R.id.ListView_destination_hot);
            tvHot.setText("爆棚指数:" + ds.getHot());
            // Characteristic
            TextView tvCharacteristic = (TextView) v
                    .findViewById(R.id.ListView_destination_characteristic);
            tvCharacteristic.setText("特色:" + ds.getCharacteristic());
            // distance
            TextView tvDistance = (TextView) v
                    .findViewById(R.id.ListView_destination_distance);
            tvDistance.setText(String.valueOf(maf.getSummary().getDistance()));
            // add view
            list.addView(v);
            // 设置监听
            v.setTag(destinationId);
            v.setOnClickListener(details);
        
        }
        list.invalidate();
    }

    Handler handler4nearby = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            String res = data.getString("value");
            Log.d(TAG,"请求周边农家乐结果为-->" + res);
            
        	//后台线程完成后更新显示结果
            showList(farmList);
            ++queryCounts;
            
            requestDataSuccess();
        }
    };
    
    Runnable runnable4nearby = new RunnableImp(LoadWay.INIT_LOAD);
    /**
     * Runnable实现类，用来请求周边农家乐list
     */
    class RunnableImp implements Runnable {
        //数据加载方式：1：初始加载, 2：选择spinner方式, 3：选择上拉加载方式
        LoadWay loadWay;
        RunnableImp(LoadWay loadWay) {
            this.loadWay = loadWay;
        }
        
        @Override
        public void run() {
        	Message msg = new Message();
            Bundle data = new Bundle();
            data.putString("value","ok");
            msg.setData(data);
            
            String Tag = "NearbyService";
            String errMsg = "读取景点概要信息出错";
            String res4MoreAroundFarm = null;
            try {
            	res4MoreAroundFarm = HttpUtil.postUrl(GET_AROUND_FARM_HOME, getParameters());
                
    			try {
    				Gson gson = new Gson();
    				farmList = gson.fromJson(res4MoreAroundFarm, 
    	            		new TypeToken<List<MoreAroundFarm>>() {}.getType());
    			} catch (JsonSyntaxException e) {
    				Log.e(TAG, "解析获取周边农家乐信息是失败", e);
    				Tools.showToastLong(AroundFarmActivity.this, "数据加载失败。");
                    return;
    			}
    			
    			//没有找到农家乐时
    			if(farmList == null || farmList.size() <= 0) {
    				Tools.showToastLong(AroundFarmActivity.this, "没有更多的农家乐了。");
                	requestDataSuccess();
                	return;
    			} 
                
            } catch (ClientProtocolException e1) {
                Log.e(Tag, errMsg, e1);
            } catch (IOException e1) {
                Log.e(Tag, errMsg, e1);
            } catch (Exception e1) {
            	Log.e(Tag, errMsg, e1);
            }
            
            handler4nearby.sendMessage(msg);
        }
    }
    
    /**
     * 请求景点列表时，传入的参数
     * 
     * @param defaultDistance 搜索的距离
     * @return 包含参数对象的list
     */
    private List<NameValuePair> getParameters() {
        List<NameValuePair> param = new ArrayList<NameValuePair>();
        
        //景点ID
        BasicNameValuePair view_id = new BasicNameValuePair("view_id", String.valueOf(farmList.get(0).getView_id()));
        param.add(view_id);
        //第几次请求
        BasicNameValuePair count = new BasicNameValuePair("count", String.valueOf(queryCounts));
        param.add(count);
        
        return param;
    }

    /**
     * 上拉刷新处理
     */
	@Override
	public void onFooterRefresh(PullToRefreshView view) {
		Log.d("请求次数", queryCounts + "");
		new Thread(new RunnableImp(LoadWay.POLLUP_LOAD)).start();
	}
	
	/**
	 * 数据请求成功时画面的显示
	 */
	private void requestDataSuccess() {
		//上方圆圈进度条消失
        if (loadingLayout != null) {
             loadingLayout.setVisibility(View.GONE);
        }
        
    	summaryLayout.setVisibility(View.VISIBLE);
        //上拉完成加载
        mPullToRefreshView.onFooterRefreshComplete();
        //可以上拉刷新
        mPullToRefreshView.unlock();
	}
	
}
