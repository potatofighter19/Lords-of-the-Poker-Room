package com.lordspoker.room;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.*;

public class GameView extends View {
    enum Screen { MENU, POKER, BLACKJACK }
    private Screen screen = Screen.MENU;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
    private final HashMap<Integer, Bitmap> avatars = new HashMap<>();
    private final ArrayList<Hit> hits = new ArrayList<>();

    private boolean soundOn = true;
    private int chips;
    private float den;

    private static final int GREEN_DARK = Color.rgb(2,25,18);
    private static final int GREEN = Color.rgb(4,91,57);
    private static final int GREEN_LIGHT = Color.rgb(8,121,76);
    private static final int GOLD = Color.rgb(217,168,50);
    private static final int GOLD_LIGHT = Color.rgb(247,213,122);
    private static final int PANEL = Color.argb(235,3,22,17);
    private static final int WHITE = Color.rgb(247,246,238);
    private static final int MUTED = Color.rgb(174,188,181);
    private static final int RED = Color.rgb(174,50,45);
    private static final int BLUE = Color.rgb(37,94,184);

    static class OpponentDef {
        final String name;
        final int res;
        OpponentDef(String n, int r) { name=n; res=r; }
    }

    private final OpponentDef[] roster = new OpponentDef[]{
        new OpponentDef("Lord Epstein", R.drawable.npc_lord_epstein),
        new OpponentDef("Greatest president", R.drawable.npc_greatest_president),
        new OpponentDef("Mr H.", R.drawable.npc_mr_h),
        new OpponentDef("Движухамэн", R.drawable.npc_dvizhuhamen),
        new OpponentDef("King of jews", R.drawable.npc_king_of_jews),
        new OpponentDef("Vinny Puh", R.drawable.npc_vinny_puh),
        new OpponentDef("No name", R.drawable.npc_no_name),
        new OpponentDef("Мистер Позитивный", R.drawable.npc_mister_pozitivniy),
        new OpponentDef("Вкусненький малышочек", R.drawable.npc_vkusnenkiy_malish),
        new OpponentDef("Riona", R.drawable.npc_riona),
        new OpponentDef("Maya suchka", R.drawable.npc_maya_suchka),
        new OpponentDef("Не лузающий", R.drawable.npc_ne_luzayushiy),
        new OpponentDef("Mr M", R.drawable.npc_mr_m),
        new OpponentDef("KaBoom4ik", R.drawable.npc_kaboom4ik),
        new OpponentDef("Unbreathable", R.drawable.npc_unbreathable),
        new OpponentDef("Saint Kirk", R.drawable.npc_saint_kirk),
        new OpponentDef("Ебучий", R.drawable.npc_ebuchiy),
        new OpponentDef("Скебоб", R.drawable.npc_skebob)
    };

    static class Hit {
        RectF r;
        String a;
        Hit(RectF rr, String aa) { r=rr; a=aa; }
    }

    static class Card {
        int r,s;
        Card(int rr,int ss){r=rr;s=ss;}
        String rank(){return r<=10?String.valueOf(r):r==11?"J":r==12?"Q":r==13?"K":"A";}
        String suit(){return s==0?"♠":s==1?"♥":s==2?"♦":"♣";}
    }

    static class Deck {
        ArrayList<Card> cards=new ArrayList<>();
        int i=0;
        Deck(int decks, Random rng){
            for(int d=0;d<decks;d++) for(int s=0;s<4;s++) for(int r=2;r<=14;r++) cards.add(new Card(r,s));
            Collections.shuffle(cards,rng);
        }
        Card draw(){return i<cards.size()?cards.get(i++):null;}
    }

    static class HandValue implements Comparable<HandValue> {
        int[] v;
        String label;
        HandValue(int[] vv,String l){v=vv;label=l;}
        @Override public int compareTo(HandValue o){
            int n=Math.max(v.length,o.v.length);
            for(int i=0;i<n;i++){
                int a=i<v.length?v[i]:0,b=i<o.v.length?o.v[i]:0;
                if(a!=b)return a-b;
            }
            return 0;
        }
    }

    static class Player {
        String name;
        int avatar;
        boolean user;
        int stack=2000;
        ArrayList<Card> hand=new ArrayList<>();
        long[] revealAt=new long[]{0,0};
        boolean folded,allIn,needsAction;
        int streetBet,totalBet;
        String action="";
        HandValue finalValue;
        long joinedAt=0;
        Player(String n,int a,boolean u){name=n;avatar=a;user=u;}
    }

    static class WinnerResult {
        Player player;
        int amount;
        String combo;
        WinnerResult(Player q,int a,String c){player=q;amount=a;combo=c;}
    }

    static class BJHand {
        ArrayList<Card> cards=new ArrayList<>();
        int bet;
        boolean stood,busted;
        String result="";
        BJHand(int b){bet=b;}
    }

    static class Confetti { float x,y,vx,vy,rot,vr; int color; long born,life; }
    static class Firework { float x,y,max; int color; long born,life; }
    static class ChipFly { float x0,y0,x1,y1; long born,life; }
    static class CardFly { Card card; float x0,y0,x1,y1,scale; boolean back; long born,life; }

    // Poker state
    private final ArrayList<Player> pp = new ArrayList<>();
    private final ArrayList<Card> board = new ArrayList<>();
    private final ArrayList<Long> boardRevealAt = new ArrayList<>();
    private final ArrayList<WinnerResult> pokerWinners = new ArrayList<>();
    private Deck pokerDeck;
    private int dealer=0, actor=-1, stage=0, currentBet=0, minRaise=20, handNo=0, raiseTarget=40;
    private boolean pokerHandOver=false, pokerShowdown=false, pokerBusy=false, pokerUserBusted=false;
    private String pokerMessage="";
    private String tableEvent="";
    private long tableEventUntil=0;

    // Blackjack state
    private Deck bjDeck;
    private OpponentDef bjDealer;
    private final ArrayList<OpponentDef> bjOthers = new ArrayList<>();
    private final ArrayList<BJHand> bjHands = new ArrayList<>();
    private final ArrayList<Card> dealerCards = new ArrayList<>();
    private final ArrayList<ArrayList<Card>> bjOtherHands = new ArrayList<>();
    private int bjBet=100;
    private boolean bjRound=false,bjDone=false,dealerReveal=false,insuranceOffered=false,insuranceTaken=false;
    private int insuranceBet=0,bjNetWin=0;
    private String bjMessage="Сделайте ставку";

    // Visual effects
    private final ArrayList<Confetti> confetti=new ArrayList<>();
    private final ArrayList<Firework> fireworks=new ArrayList<>();
    private final ArrayList<ChipFly> chipFlys=new ArrayList<>();
    private final ArrayList<CardFly> cardFlys=new ArrayList<>();

    public GameView(Context c){
        super(c);
        den=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        prefs=c.getSharedPreferences("lords_poker",Context.MODE_PRIVATE);
        chips=prefs.getInt("chips",5000);
        soundOn=prefs.getBoolean("sound",true);
        for(OpponentDef d:roster) avatars.put(d.res, BitmapFactory.decodeResource(getResources(),d.res));
        setKeepScreenOn(true);
    }

    private float dp(float v){return v*den;}
    private void save(){prefs.edit().putInt("chips",chips).putBoolean("sound",soundOn).apply();}
    private boolean landscape(){return getWidth()>getHeight();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        hits.clear();
        drawBackground(c);
        if(screen==Screen.MENU)drawMenu(c);
        else if(screen==Screen.POKER)drawPoker(c);
        else drawBlackjack(c);
        drawEffects(c);
    }

    private void drawBackground(Canvas c){
        LinearGradient g=new LinearGradient(0,0,0,getHeight(),Color.rgb(2,25,18),Color.BLACK,Shader.TileMode.CLAMP);
        p.setShader(g);c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);
    }

    private void txt(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){
        p.setColor(color);p.setTextSize(dp(size));p.setTextAlign(align);
        p.setTypeface(bold?Typeface.create("sans",Typeface.BOLD):Typeface.create("sans",Typeface.NORMAL));
        c.drawText(s,x,y,p);
    }

    private void txtFit(Canvas c,String s,float x,float y,float wanted,float min,float maxWidth,int color,Paint.Align align,boolean bold){
        float size=wanted;
        p.setTypeface(bold?Typeface.create("sans",Typeface.BOLD):Typeface.create("sans",Typeface.NORMAL));
        p.setTextAlign(align);
        while(size>min){p.setTextSize(dp(size));if(p.measureText(s)<=maxWidth)break;size-=0.5f;}
        txt(c,s,x,y,size,color,align,bold);
    }

    private void round(Canvas c,RectF r,float rad,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,dp(rad),dp(rad),p);}
    private void outline(Canvas c,RectF r,float rad,int color,float sw){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(sw));p.setColor(color);c.drawRoundRect(r,dp(rad),dp(rad),p);p.setStyle(Paint.Style.FILL);}

    private void button(Canvas c,RectF r,String label,String action,boolean primary){
        round(c,r,12,primary?GOLD:PANEL);
        outline(c,r,12,primary?GOLD_LIGHT:Color.rgb(50,72,64),1.2f);
        txtFit(c,label,r.centerX(),r.centerY()+dp(5),primary?14:13,8,r.width()-dp(10),primary?Color.rgb(24,22,13):WHITE,Paint.Align.CENTER,true);
        RectF hr=new RectF(r);hr.inset(-dp(5),-dp(5));hits.add(new Hit(hr,action));
    }

    private float topBarHeight(){return landscape()?dp(60):dp(82);}

    private void topBar(Canvas c){
        float h=landscape()?dp(52):dp(60);
        p.setColor(Color.argb(248,2,25,18));c.drawRect(0,0,getWidth(),h,p);
        txt(c,"♠",dp(16),h*.66f,25,GOLD,Paint.Align.LEFT,true);
        float right=getWidth()-dp(18);
        txtFit(c,"LORDS OF THE POKER ROOM",dp(48),h*.63f,landscape()?15:12,8,right-dp(48),WHITE,Paint.Align.LEFT,true);
    }

    private void topBar(Canvas c,String status,String mode){
        float h=topBarHeight();
        p.setColor(Color.argb(248,2,25,18));c.drawRect(0,0,getWidth(),h,p);
        boolean land=landscape();
        float mainY=land?dp(27):dp(29);
        txt(c,"♠",dp(15),mainY+dp(2),23,GOLD,Paint.Align.LEFT,true);

        float soundW=dp(42);
        RectF snd=new RectF(getWidth()-soundW-dp(8),dp(7),getWidth()-dp(8),land?dp(43):dp(45));
        round(c,snd,11,Color.argb(180,8,39,29));
        txt(c,soundOn?"🔊":"🔇",snd.centerX(),snd.centerY()+dp(6),16,WHITE,Paint.Align.CENTER,false);
        hits.add(new Hit(new RectF(snd.left-dp(4),snd.top-dp(4),snd.right+dp(4),snd.bottom+dp(4)),"sound"));

        float moneyRight=snd.left-dp(10);
        String money="◎ "+chips;
        txt(c,money,moneyRight,mainY+dp(1),13,GOLD_LIGHT,Paint.Align.RIGHT,true);
        float titleMax=land?getWidth()*.27f:Math.max(dp(70),moneyRight-dp(62)-dp(14));
        txtFit(c,"LORDS OF THE POKER ROOM",dp(48),mainY+dp(1),land?13:10,7,titleMax,WHITE,Paint.Align.LEFT,true);

        if(land){
            float sx=getWidth()*.52f;
            float sw=Math.min(dp(280),getWidth()*.32f);
            RectF sr=new RectF(sx-sw/2,dp(8),sx+sw/2,dp(44));
            round(c,sr,12,Color.argb(180,5,45,32));outline(c,sr,12,Color.argb(120,217,168,50),1);
            txtFit(c,status,sr.centerX(),sr.centerY()+dp(4),9,6.5f,sr.width()-dp(12),WHITE,Paint.Align.CENTER,true);
            txt(c,mode,dp(15),h-dp(6),7.5f,MUTED,Paint.Align.LEFT,true);
        }else{
            RectF sr=new RectF(dp(10),dp(48),getWidth()-dp(10),h-dp(5));
            round(c,sr,10,Color.argb(190,5,45,32));
            txtFit(c,status,sr.centerX(),sr.centerY()+dp(3.5f),9,6.5f,sr.width()-dp(110),WHITE,Paint.Align.CENTER,true);
            txt(c,mode,sr.left+dp(9),sr.centerY()+dp(3),7,MUTED,Paint.Align.LEFT,true);
        }
    }

    private void drawMenu(Canvas c){
        topBar(c);
        float w=getWidth(),h=getHeight();boolean land=landscape();
        float y=land?h*.25f:h*.24f;
        txt(c,"LORDS OF THE",w/2,y,land?31:29,WHITE,Paint.Align.CENTER,true);
        txt(c,"POKER ROOM",w/2,y+dp(38),land?31:29,GOLD_LIGHT,Paint.Align.CENTER,true);
        txtFit(c,"Сразитесь с сильнейшими игроками в карты со всего мира",w/2,y+dp(72),land?11:10,7,w-dp(26),MUTED,Paint.Align.CENTER,false);
        float bw=land?Math.min(dp(300),w*.34f):w*.78f,bh=dp(72),gap=dp(18);float bx=(w-bw)/2,by=y+dp(105);
        button(c,new RectF(bx,by,bx+bw,by+bh),"BADASS HOLDEM","menuPoker",true);
        button(c,new RectF(bx,by+bh+gap,bx+bw,by+2*bh+gap),"BLACKJACKPORTER","menuBJ",false);
        txt(c,"Native Patch 1",w/2,h-dp(22),8,Color.rgb(116,139,129),Paint.Align.CENTER,false);
    }

    private RectF tableRect(){
        float margin=dp(6),top=(screen==Screen.MENU?dp(60):topBarHeight())+dp(4);
        return new RectF(margin,top,getWidth()-margin,getHeight()-dp(5));
    }

    private void drawTable(Canvas c,RectF t){
        p.setColor(Color.rgb(64,38,23));c.drawRoundRect(t,t.width()*.19f,t.height()*.14f,p);
        RectF g=new RectF(t);g.inset(dp(7),dp(7));p.setColor(GOLD);c.drawRoundRect(g,g.width()*.19f,g.height()*.14f,p);
        RectF felt=new RectF(g);felt.inset(dp(5),dp(5));
        RadialGradient rg=new RadialGradient(felt.centerX(),felt.centerY(),Math.max(felt.width(),felt.height())*.55f,GREEN_LIGHT,GREEN_DARK,Shader.TileMode.CLAMP);
        p.setShader(rg);c.drawRoundRect(felt,felt.width()*.19f,felt.height()*.14f,p);p.setShader(null);
        outline(c,new RectF(felt.left+dp(15),felt.top+dp(15),felt.right-dp(15),felt.bottom-dp(15)),felt.width()*.18f,Color.argb(70,220,191,112),1);
        if(landscape()){
            txtFit(c,"LORDS OF THE POKER ROOM",felt.centerX(),felt.centerY()+dp(8),Math.max(18,Math.min(31,felt.width()/40)),12,felt.width()*.55f,Color.argb(30,255,255,255),Paint.Align.CENTER,true);
        }
    }

    private ArrayList<OpponentDef> randomOpponents(int n){
        ArrayList<OpponentDef> a=new ArrayList<>(Arrays.asList(roster));Collections.shuffle(a,rng);
        return new ArrayList<>(a.subList(0,Math.min(n,a.size())));
    }

    // ---------------- Poker ----------------
    private void newPokerRoster(){
        pp.clear();
        Player u=new Player("Вы",0,true);u.joinedAt=System.currentTimeMillis();pp.add(u);
        int opponents=4+rng.nextInt(4); // 5-8 players total
        for(OpponentDef d:randomOpponents(opponents)){
            Player q=new Player(d.name,d.res,false);q.joinedAt=System.currentTimeMillis();pp.add(q);
        }
        dealer=rng.nextInt(pp.size());handNo=0;pokerUserBusted=false;startPokerHand();
    }

    private int nextSeat(int from){return (from+1)%pp.size();}
    private int activeCount(){int n=0;for(Player q:pp)if(!q.folded)n++;return n;}
    private int actionableCount(){int n=0;for(Player q:pp)if(!q.folded&&!q.allIn)n++;return n;}
    private int pot(){int z=0;for(Player q:pp)z+=q.totalBet;return z;}

    private OpponentDef replacementForSeat(){
        HashSet<String> used=new HashSet<>();for(Player q:pp)used.add(q.name);
        ArrayList<OpponentDef> candidates=new ArrayList<>();for(OpponentDef d:roster)if(!used.contains(d.name))candidates.add(d);
        if(candidates.isEmpty())candidates.addAll(Arrays.asList(roster));
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private void rotateBustedOpponents(){
        long now=System.currentTimeMillis();
        ArrayList<String> events=new ArrayList<>();
        for(int i=1;i<pp.size();i++){
            Player old=pp.get(i);
            if(old.stack<=0){
                OpponentDef d=replacementForSeat();
                Player fresh=new Player(d.name,d.res,false);fresh.joinedAt=now;pp.set(i,fresh);
                events.add(old.name+" ушёл · "+d.name+" сел за стол");
            }
        }
        if(!events.isEmpty()){
            tableEvent=events.get(0);tableEventUntil=now+2600;
        }
    }

    private void startPokerHand(){
        if(pp.isEmpty())return;
        pokerWinners.clear();pokerShowdown=false;pokerBusy=false;board.clear();boardRevealAt.clear();cardFlys.clear();chipFlys.clear();stage=0;currentBet=0;minRaise=20;raiseTarget=40;
        if(pp.get(0).stack<=0){
            pokerUserBusted=true;pokerHandOver=true;actor=-1;pokerMessage="Ваш стек закончился";invalidate();return;
        }
        rotateBustedOpponents();
        handNo++;pokerHandOver=false;pokerUserBusted=false;pokerDeck=new Deck(1,rng);dealer=nextSeat(dealer);
        for(Player q:pp){q.hand.clear();q.folded=false;q.allIn=false;q.needsAction=false;q.streetBet=0;q.totalBet=0;q.action="";q.finalValue=null;q.revealAt[0]=q.revealAt[1]=0;}
        long base=System.currentTimeMillis()+100;
        for(int r=0;r<2;r++){
            for(int k=1;k<=pp.size();k++){
                int i=(dealer+k)%pp.size();Player q=pp.get(i);q.hand.add(pokerDeck.draw());
                q.revealAt[r]=base+(r*pp.size()+k-1)*85L+360;
            }
        }
        animatePokerDeal(base);
        int sb,bb;if(pp.size()==2){sb=dealer;bb=nextSeat(dealer);}else{sb=nextSeat(dealer);bb=nextSeat(sb);}
        postBet(sb,10,"SB 10");postBet(bb,20,"BB 20");currentBet=20;minRaise=20;raiseTarget=40;
        for(Player q:pp)if(!q.allIn)q.needsAction=true;
        actor=pp.size()==2?dealer:nextSeat(bb);actor=findNextNeed(actor-1<0?pp.size()-1:actor-1);
        pokerMessage="Раздача #"+handNo+" · Preflop";
        pokerBusy=true;invalidate();
        final long dealDelay=2L*pp.size()*85L+430L;
        handler.postDelayed(new Runnable(){@Override public void run(){pokerBusy=false;invalidate();scheduleAI();}},dealDelay);
    }

    private void animatePokerDeal(long base){
        RectF t=tableRect();boolean land=landscape();
        float sx=t.centerX(),sy=t.top+t.height()*.42f;
        int seq=0;
        for(int r=0;r<2;r++) for(int k=1;k<=pp.size();k++){
            int idx=(dealer+k)%pp.size();PointF dest=seatPos(t,idx,land);
            CardFly f=new CardFly();f.card=pp.get(idx).hand.get(r);f.back=!pp.get(idx).user;f.x0=sx;f.y0=sy;f.x1=dest.x+(r==0?-dp(12):dp(12));f.y1=dest.y+dp(28);f.scale=.72f;f.born=base+seq*85L;f.life=360;cardFlys.add(f);seq++;
        }
    }

    private void postBet(int idx,int amount,String label){
        Player q=pp.get(idx);int pay=Math.min(q.stack,amount);q.stack-=pay;q.streetBet+=pay;q.totalBet+=pay;if(q.stack==0)q.allIn=true;q.action=label;
    }

    private int findNextNeed(int from){
        int i=from;for(int k=0;k<pp.size();k++){i=nextSeat(i);Player q=pp.get(i);if(!q.folded&&!q.allIn&&q.needsAction)return i;}return -1;
    }

    private void pokerAction(String type){
        if(pokerBusy||pokerHandOver||actor<0||!pp.get(actor).user)return;
        Player q=pp.get(actor);boolean raised=false;
        if(type.equals("fold")){q.folded=true;q.needsAction=false;q.action="Fold";}
        else if(type.equals("checkcall")){
            int need=currentBet-q.streetBet;if(need<=0)q.action="Check";else{int pay=Math.min(q.stack,need);q.stack-=pay;q.streetBet+=pay;q.totalBet+=pay;q.action=pay<need?"All-in "+pay:"Call "+pay;if(q.stack==0)q.allIn=true;animateBet(actor);}q.needsAction=false;
        } else if(type.equals("raise")){
            int target=Math.max(currentBet+minRaise,raiseTarget);target=Math.min(target,q.streetBet+q.stack);raised=raiseTo(actor,target);
        } else if(type.equals("allin")){
            raised=raiseTo(actor,q.streetBet+q.stack);
        }
        afterPokerAction(actor,raised);
    }

    private boolean raiseTo(int idx,int target){
        Player q=pp.get(idx);int old=currentBet;int pay=Math.max(0,target-q.streetBet);pay=Math.min(pay,q.stack);q.stack-=pay;q.streetBet+=pay;q.totalBet+=pay;if(q.stack==0)q.allIn=true;
        boolean raised=q.streetBet>old;
        if(raised){
            int inc=q.streetBet-old;if(inc>=minRaise)minRaise=inc;currentBet=q.streetBet;
            for(int i=0;i<pp.size();i++){Player o=pp.get(i);if(i!=idx&&!o.folded&&!o.allIn)o.needsAction=true;}
            q.action=(q.allIn?"All-in ":"Raise ")+q.streetBet;raiseTarget=Math.min(q.streetBet+q.stack,Math.max(currentBet+minRaise,currentBet*2));
        }else q.action=q.allIn?"All-in":"Call";
        q.needsAction=false;animateBet(idx);return raised;
    }

    private void afterPokerAction(int idx,boolean raised){
        pp.get(idx).needsAction=false;
        if(activeCount()==1){awardLast();return;}
        if(actionableCount()==0){runoutAndShowdown();return;}
        int nx=findNextNeed(idx);if(nx<0){endPokerStreet();return;}
        actor=nx;raiseTarget=Math.max(currentBet+minRaise,currentBet==0?20:currentBet*2);invalidate();scheduleAI();
    }

    private void endPokerStreet(){
        animateCollectBets();
        for(Player q:pp){q.streetBet=0;q.needsAction=false;q.action="";}
        currentBet=0;minRaise=20;stage++;
        long base=System.currentTimeMillis()+150;
        if(stage==1){addBoardCard(base,0);addBoardCard(base+110,1);addBoardCard(base+220,2);pokerMessage="Раздача #"+handNo+" · Flop";}
        else if(stage==2){addBoardCard(base,3);pokerMessage="Раздача #"+handNo+" · Turn";}
        else if(stage==3){addBoardCard(base,4);pokerMessage="Раздача #"+handNo+" · River";}
        else{showdownPoker();return;}
        if(actionableCount()<=1){runoutAndShowdown();return;}
        for(Player q:pp)if(!q.folded&&!q.allIn)q.needsAction=true;
        actor=findNextNeed(dealer);raiseTarget=20;pokerBusy=true;invalidate();
        final long streetDelay=stage==1?760L:540L;
        handler.postDelayed(new Runnable(){@Override public void run(){pokerBusy=false;invalidate();scheduleAI();}},streetDelay);
    }

    private void addBoardCard(long reveal,int index){
        Card card=pokerDeck.draw();board.add(card);boardRevealAt.add(reveal+360);
        RectF t=tableRect();boolean land=landscape();float cy=t.centerY()+(land?dp(10):-dp(6));float gap=land?dp(45):Math.min(dp(46),t.width()/6f);float tx=t.centerX()-gap*2+index*gap;
        CardFly f=new CardFly();f.card=card;f.back=false;f.x0=t.centerX();f.y0=t.top+t.height()*.40f;f.x1=tx;f.y1=cy;f.scale=land?.78f:.86f;f.born=reveal;f.life=360;cardFlys.add(f);
    }

    private void runoutAndShowdown(){
        long base=System.currentTimeMillis()+100;int index=board.size();while(board.size()<5){addBoardCard(base+(board.size()-index)*100L,board.size());}stage=4;
        actor=-1;pokerBusy=true;stage=4;
        handler.postDelayed(new Runnable(){@Override public void run(){pokerBusy=false;showdownPoker();}},950);
    }

    private void awardLast(){
        animateCollectBets();
        int win=-1;for(int i=0;i<pp.size();i++)if(!pp.get(i).folded){win=i;break;}
        int total=pot();
        if(win>=0){Player w=pp.get(win);w.stack+=total;w.action="WIN +"+total;pokerWinners.add(new WinnerResult(w,total,"Без вскрытия"));animatePotTo(win);if(w.user)celebrate();}
        for(Player q:pp){q.streetBet=0;q.totalBet=0;}
        pokerMessage="Банк забран без вскрытия";pokerHandOver=true;actor=-1;invalidate();
    }

    private void showdownPoker(){
        if(pokerHandOver)return;
        animateCollectBets();pokerShowdown=true;pokerHandOver=true;actor=-1;pokerWinners.clear();
        for(Player q:pp)if(!q.folded){ArrayList<Card> seven=new ArrayList<>(q.hand);seven.addAll(board);q.finalValue=evaluate(seven);}
        TreeSet<Integer> levels=new TreeSet<>();for(Player q:pp)if(q.totalBet>0)levels.add(q.totalBet);
        int prev=0;HashMap<Player,Integer> won=new HashMap<>();
        for(int level:levels){
            int contributors=0;for(Player q:pp)if(q.totalBet>=level)contributors++;
            int side=(level-prev)*contributors;prev=level;if(side<=0)continue;
            ArrayList<Player> elig=new ArrayList<>();for(Player q:pp)if(!q.folded&&q.totalBet>=level)elig.add(q);if(elig.isEmpty())continue;
            HandValue best=null;for(Player q:elig)if(best==null||q.finalValue.compareTo(best)>0)best=q.finalValue;
            ArrayList<Player> ws=new ArrayList<>();for(Player q:elig)if(q.finalValue.compareTo(best)==0)ws.add(q);
            int share=side/ws.size(),rem=side%ws.size();
            for(int i=0;i<ws.size();i++){Player q=ws.get(i);int add=share+(i<rem?1:0);q.stack+=add;won.put(q,won.containsKey(q)?won.get(q)+add:add);}
        }
        boolean userWon=false;
        for(Player q:won.keySet()){
            int amount=won.get(q);q.action="WIN +"+amount;pokerWinners.add(new WinnerResult(q,amount,q.finalValue!=null?q.finalValue.label:"Комбинация"));
            int idx=pp.indexOf(q);if(idx>=0)animatePotTo(idx);if(q.user){userWon=true;}
        }
        Collections.sort(pokerWinners,new Comparator<WinnerResult>(){@Override public int compare(WinnerResult a,WinnerResult b){return b.amount-a.amount;}});
        if(userWon)celebrate();for(Player q:pp){q.streetBet=0;q.totalBet=0;}pokerMessage="Вскрытие";invalidate();
    }

    private void scheduleAI(){
        if(actor<0||pokerHandOver||pp.get(actor).user)return;
        pokerBusy=true;handler.postDelayed(new Runnable(){@Override public void run(){pokerBusy=false;if(actor<0||pokerHandOver||pp.get(actor).user)return;aiPoker(actor);}},380+rng.nextInt(420));
    }

    private void aiPoker(int idx){
        Player q=pp.get(idx);int need=currentBet-q.streetBet;double strength=aiStrength(q);double pressure=q.stack==0?1:(double)need/Math.max(1,q.stack);double roll=rng.nextDouble();boolean raised=false;
        if(need>0&&strength<0.28&&pressure>0.08&&roll<0.70){q.folded=true;q.action="Fold";q.needsAction=false;}
        else if((strength>0.77&&roll<0.52)||(strength>0.58&&roll<0.18)){int target=Math.min(q.streetBet+q.stack,Math.max(currentBet+minRaise,currentBet==0?20:currentBet*2));raised=raiseTo(idx,target);}
        else{int pay=Math.min(q.stack,need);q.stack-=pay;q.streetBet+=pay;q.totalBet+=pay;if(q.stack==0)q.allIn=true;q.action=need==0?"Check":pay<need?"All-in "+pay:"Call "+pay;q.needsAction=false;if(pay>0)animateBet(idx);}
        afterPokerAction(idx,raised);
    }

    private double aiStrength(Player q){
        if(stage==0){Card a=q.hand.get(0),b=q.hand.get(1);double x=(a.r+b.r)/28.0;if(a.r==b.r)x=.55+a.r/30.0;if(a.s==b.s)x+=.06;if(Math.abs(a.r-b.r)<=2)x+=.04;if(a.r>=13||b.r>=13)x+=.06;return Math.min(1,x);}
        ArrayList<Card> a=new ArrayList<>(q.hand);a.addAll(board);HandValue hv=evaluate(a);return Math.min(1,.18+hv.v[0]*.115+(hv.v.length>1?hv.v[1]/100.0:0));
    }

    private String stageName(){return stage==0?"Preflop":stage==1?"Flop":stage==2?"Turn":stage==3?"River":"Showdown";}

    private String pokerStatus(){
        long now=System.currentTimeMillis();
        if(now<tableEventUntil&&!tableEvent.isEmpty())return tableEvent;
        if(pokerUserBusted)return "Ваш стек закончился";
        if(pokerHandOver)return pokerMessage;
        if(actor>=0){Player q=pp.get(actor);return q.user?"Ваш ход · "+stageName():q.name+" ходит · "+stageName();}
        return pokerMessage;
    }

    private HandValue userCurrent(){
        if(pp.isEmpty()||pp.get(0).hand.size()<2)return null;Player u=pp.get(0);
        if(stage==0){Card a=u.hand.get(0),b=u.hand.get(1);if(a.r==b.r)return new HandValue(new int[]{1,a.r},"Карманная пара · "+a.rank());return new HandValue(new int[]{0,Math.max(a.r,b.r)},a.rank()+a.suit()+"  "+b.rank()+b.suit());}
        ArrayList<Card>x=new ArrayList<>(u.hand);x.addAll(board);return evaluate(x);
    }

    private void drawPoker(Canvas c){
        topBar(c,pokerStatus(),"BADASS HOLDEM");RectF t=tableRect();drawTable(c,t);boolean land=landscape();
        drawPokerSeats(c,t,land);drawPokerCenter(c,t,land);drawPokerControls(c,t,land);
        drawBack(c);
        if(pokerHandOver)drawPokerResult(c,t,land);
        if(!pokerHandOver&&actor>=0)postInvalidateOnAnimation();
    }

    private void drawBack(Canvas c){
        float top=topBarHeight()+dp(8);RectF back=new RectF(dp(10),top,dp(50),top+dp(38));round(c,back,10,Color.argb(185,4,30,22));txt(c,"‹",back.centerX(),back.centerY()+dp(8),24,WHITE,Paint.Align.CENTER,true);hits.add(new Hit(new RectF(back.left-dp(4),back.top-dp(4),back.right+dp(4),back.bottom+dp(4)),"back"));
    }

    private PointF seatPos(RectF t,int idx,boolean land){
        if(idx==0)return new PointF(t.centerX(),t.bottom-(land?dp(145):dp(165)));
        int n=pp.size()-1;double deg;
        if(n==1)deg=270;
        else if(n==2)deg=(idx==1?215:325);
        else if(n==3){double[] a={180,270,360};deg=a[idx-1];}
        else if(n==4){double[] a={160,230,310,20};deg=a[idx-1];}
        else deg=135.0+(idx-1)*(270.0/(n-1));
        double a=Math.toRadians(deg);
        float rx=t.width()*(land?.405f:.405f),ry=t.height()*(land?.36f:.375f);
        float x=t.centerX()+(float)Math.cos(a)*rx;float y=t.centerY()+(float)Math.sin(a)*ry;
        float padX=land?dp(62):dp(54),padTop=land?dp(42):dp(48),padBottom=land?dp(115):dp(135);
        x=Math.max(t.left+padX,Math.min(t.right-padX,x));y=Math.max(t.top+padTop,Math.min(t.bottom-padBottom,y));return new PointF(x,y);
    }

    private void drawPokerSeats(Canvas c,RectF t,boolean land){for(int i=0;i<pp.size();i++)drawPokerSeat(c,t,i,seatPos(t,i,land),land);}

    private void drawPokerSeat(Canvas c,RectF t,int idx,PointF pos,boolean land){
        Player q=pp.get(idx);int count=pp.size();
        float sw=q.user?(land?dp(150):dp(156)):(land?(count>=7?dp(112):dp(126)):(count>=7?dp(104):dp(116)));
        float sh=q.user?(land?dp(78):dp(88)):(land?dp(68):dp(74));
        RectF box=new RectF(pos.x-sw/2,pos.y-sh/2,pos.x+sw/2,pos.y+sh/2);
        int bg=q.folded?Color.argb(150,3,20,16):PANEL;round(c,box,11,bg);
        long now=System.currentTimeMillis();
        if(idx==actor){float pulse=(float)(.5+.5*Math.sin(now/180.0));outline(c,new RectF(box.left-dp(2)-pulse*dp(2),box.top-dp(2)-pulse*dp(2),box.right+dp(2)+pulse*dp(2),box.bottom+dp(2)+pulse*dp(2)),12,Color.argb((int)(150+90*pulse),217,168,50),2.2f);}
        else if(now-q.joinedAt<2300)outline(c,box,11,GOLD_LIGHT,2);

        float av=q.user?(land?dp(50):dp(52)):(land?dp(43):dp(44));
        RectF ar=new RectF(box.left+dp(5),box.centerY()-av/2,box.left+dp(5)+av,box.centerY()+av/2);
        if(q.user){round(c,ar,9,Color.rgb(30,70,57));txt(c,"YOU",ar.centerX(),ar.centerY()+dp(6),15,WHITE,Paint.Align.CENTER,true);}else drawAvatar(c,q.avatar,ar,9);
        outline(c,ar,9,GOLD,1.2f);
        float tx=ar.right+dp(5),tw=box.right-tx-dp(4);
        drawName(c,q.name,tx,box.top+dp(18),tw,q.user?9.5f:8.2f);
        txt(c,"◎ "+q.stack,tx,box.top+(q.name.contains(" ")?dp(45):dp(36)),8.5f,GOLD_LIGHT,Paint.Align.LEFT,true);
        if(!q.action.isEmpty())txtFit(c,q.action,tx,box.bottom-dp(7),7.2f,5.8f,tw,MUTED,Paint.Align.LEFT,false);

        long tnow=System.currentTimeMillis();
        if(q.hand.size()>=2){
            if(!q.user){
                float cy=box.bottom+dp(16);for(int k=0;k<2;k++)if(tnow>=q.revealAt[k]){float cx=box.centerX()+(k==0?-dp(13):dp(13));if(pokerShowdown&&!q.folded)drawMiniCard(c,q.hand.get(k),cx,cy,true);else drawCardBack(c,cx,cy,true);}
                if(pokerShowdown&&!q.folded&&q.finalValue!=null)txtFit(c,q.finalValue.label,box.centerX(),box.bottom+dp(29),7,5.2f,sw+dp(20),GOLD_LIGHT,Paint.Align.CENTER,true);
            }else{
                float cy=box.bottom+dp(23);for(int k=0;k<2;k++)if(tnow>=q.revealAt[k])drawMiniCard(c,q.hand.get(k),box.centerX()+(k==0?-dp(18):dp(18)),cy,false);
                if(pokerShowdown&&q.finalValue!=null)txtFit(c,q.finalValue.label,box.centerX(),box.bottom+dp(39),7.3f,5.5f,sw+dp(40),GOLD_LIGHT,Paint.Align.CENTER,true);
            }
        }
        if(q.streetBet>0&&!q.folded)drawSeatBet(c,t,pos,q.streetBet,land);
    }

    private void drawName(Canvas c,String name,float x,float y,float maxWidth,float size){
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(dp(size));
        if(p.measureText(name)<=maxWidth){txt(c,name,x,y,size,WHITE,Paint.Align.LEFT,true);return;}
        int split=name.lastIndexOf(' ');if(split>0){String a=name.substring(0,split),b=name.substring(split+1);txtFit(c,a,x,y,size,6,maxWidth,WHITE,Paint.Align.LEFT,true);txtFit(c,b,x,y+dp(10),size,6,maxWidth,WHITE,Paint.Align.LEFT,true);}else txtFit(c,name,x,y,size,5.8f,maxWidth,WHITE,Paint.Align.LEFT,true);
    }

    private void drawAvatar(Canvas c,int res,RectF r,float rad){
        Bitmap b=avatars.get(res);if(b==null){round(c,r,rad,Color.DKGRAY);return;}
        Path path=new Path();path.addRoundRect(r,dp(rad),dp(rad),Path.Direction.CW);c.save();c.clipPath(path);Rect src=coverSrc(b,r);c.drawBitmap(b,src,r,p);c.restore();
    }

    private Rect coverSrc(Bitmap b,RectF dest){
        float sr=(float)b.getWidth()/b.getHeight(),dr=dest.width()/dest.height();
        if(sr>dr){int nw=(int)(b.getHeight()*dr),l=(b.getWidth()-nw)/2;return new Rect(l,0,l+nw,b.getHeight());}
        int nh=(int)(b.getWidth()/dr),top=(b.getHeight()-nh)/2;return new Rect(0,top,b.getWidth(),top+nh);
    }

    private void drawSeatBet(Canvas c,RectF t,PointF seat,int amount,boolean land){
        float x=seat.x+(t.centerX()-seat.x)*.34f,y=seat.y+(t.centerY()-seat.y)*.34f;drawChipStack(c,x,y,amount,.58f);txt(c,""+amount,x,y+dp(18),6.5f,WHITE,Paint.Align.CENTER,true);
    }

    private void drawPokerCenter(Canvas c,RectF t,boolean land){
        float cy=t.centerY()+(land?dp(12):-dp(4));
        float pileY=cy-(land?dp(74):dp(105));drawPotPile(c,t.centerX(),pileY,pot());
        txt(c,"БАНК ◎ "+pot(),t.centerX(),pileY-dp(18),9,GOLD_LIGHT,Paint.Align.CENTER,true);
        float gap=land?dp(45):Math.min(dp(46),t.width()/6f),start=t.centerX()-gap*2;long now=System.currentTimeMillis();
        for(int i=0;i<board.size();i++)if(i<boardRevealAt.size()&&now>=boardRevealAt.get(i))drawFullCard(c,board.get(i),start+i*gap,cy,land?.78f:.86f);
        HandValue hv=userCurrent();
        if(hv!=null&&!pokerHandOver){
            RectF cr=new RectF(t.centerX()-dp(108),cy+(land?dp(42):dp(55)),t.centerX()+dp(108),cy+(land?dp(70):dp(84)));round(c,cr,9,Color.argb(220,3,27,20));
            txt(c,"ВАША КОМБИНАЦИЯ",cr.centerX(),cr.top+dp(10),6.7f,GOLD_LIGHT,Paint.Align.CENTER,true);txtFit(c,hv.label,cr.centerX(),cr.bottom-dp(6),9,6.4f,cr.width()-dp(12),WHITE,Paint.Align.CENTER,true);
        }
    }

    private void drawChipStack(Canvas c,float x,float y,int amount,float scale){
        if(amount<=0)return;int chipsToDraw=Math.min(12,Math.max(1,2+(int)(Math.log10(amount+1)*3)));float rr=dp(6.5f)*scale,step=dp(3.2f)*scale;
        int[] colors={Color.rgb(174,46,42),BLUE,Color.rgb(26,118,74),GOLD,Color.rgb(38,38,42)};
        for(int i=0;i<chipsToDraw;i++){float yy=y-i*step;p.setColor(colors[(i+amount/20)%colors.length]);c.drawOval(new RectF(x-rr,yy-rr*.45f,x+rr,yy+rr*.45f),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.8f));p.setColor(Color.argb(190,255,255,255));c.drawOval(new RectF(x-rr,yy-rr*.45f,x+rr,yy+rr*.45f),p);p.setStyle(Paint.Style.FILL);}
    }

    private void drawPotPile(Canvas c,float x,float y,int amount){
        if(amount<=0)return;int cols=Math.min(7,1+amount/180);float spread=dp(13);for(int col=0;col<cols;col++){int part=Math.max(20,amount/cols+col*7);float ox=(col-(cols-1)/2f)*spread;float oy=Math.abs(col-(cols-1)/2f)*dp(2);drawChipStack(c,x+ox,y+oy,part,.75f);}
    }

    private void drawPokerControls(Canvas c,RectF t,boolean land){
        if(pokerHandOver||pokerBusy||actor<0||!pp.get(actor).user)return;Player u=pp.get(actor);
        float h=land?dp(52):dp(62),w=Math.min(t.width()*.78f,land?dp(690):t.width()*.94f),x=t.centerX()-w/2,y=t.bottom-h-dp(7);
        RectF panel=new RectF(x,y,x+w,y+h);round(c,panel,15,Color.argb(247,1,18,14));float gap=dp(5),bw=(w-gap*5)/4,by=y+dp(7),bh=h-dp(14);
        RectF f=new RectF(x+gap,by,x+gap+bw,by+bh);button(c,f,"FOLD","fold",false);
        int need=currentBet-u.streetBet;RectF cc=new RectF(f.right+gap,by,f.right+gap+bw,by+bh);button(c,cc,need<=0?"CHECK":"CALL "+need,"checkcall",false);
        RectF rr=new RectF(cc.right+gap,by,cc.right+gap+bw,by+bh);button(c,rr,(currentBet==0?"BET ":"RAISE ")+raiseTarget,"raise",true);
        RectF aa=new RectF(rr.right+gap,by,rr.right+gap+bw,by+bh);button(c,aa,"ALL-IN "+u.stack,"allin",false);
        if(!land){RectF minus=new RectF(rr.left,rr.top-dp(36),rr.left+dp(38),rr.top-dp(3));RectF plus=new RectF(rr.right-dp(38),rr.top-dp(36),rr.right,rr.top-dp(3));button(c,minus,"−","raiseMinus",false);button(c,plus,"+","raisePlus",false);}
    }

    private void drawPokerResult(Canvas c,RectF t,boolean land){
        float w=Math.min(t.width()*.82f,dp(520)),h;if(pokerUserBusted)h=land?dp(150):dp(185);else h=Math.min(land?dp(210):dp(300),t.height()*.58f);
        RectF r=new RectF(t.centerX()-w/2,t.centerY()-h/2,t.centerX()+w/2,t.centerY()+h/2);round(c,r,18,Color.argb(248,2,22,17));outline(c,r,18,GOLD,2);
        if(pokerUserBusted){txt(c,"СТЕК ЗАКОНЧИЛСЯ",r.centerX(),r.top+dp(38),18,GOLD_LIGHT,Paint.Align.CENTER,true);txt(c,"Начните новый стол с новым стеком",r.centerX(),r.top+dp(67),10,MUTED,Paint.Align.CENTER,false);RectF b=new RectF(r.centerX()-dp(95),r.bottom-dp(62),r.centerX()+dp(95),r.bottom-dp(14));button(c,b,"НОВЫЙ СТОЛ","newPokerTable",true);return;}
        txt(c,pokerWinners.size()>1?"ПОБЕДИТЕЛИ":"ПОБЕДИТЕЛЬ",r.centerX(),r.top+dp(27),13,GOLD_LIGHT,Paint.Align.CENTER,true);
        int shown=Math.min(3,pokerWinners.size());float rowH=land?dp(53):dp(62),start=r.top+dp(42);
        for(int i=0;i<shown;i++){
            WinnerResult wr=pokerWinners.get(i);float yy=start+i*rowH;float av=land?dp(42):dp(48);RectF ar=new RectF(r.left+dp(18),yy,r.left+dp(18)+av,yy+av);
            if(wr.player.user){round(c,ar,9,Color.rgb(30,70,57));txt(c,"YOU",ar.centerX(),ar.centerY()+dp(5),12,WHITE,Paint.Align.CENTER,true);}else drawAvatar(c,wr.player.avatar,ar,9);outline(c,ar,9,GOLD,1.2f);
            float tx=ar.right+dp(10);txtFit(c,wr.player.name,tx,yy+dp(16),11,7,r.right-tx-dp(76),WHITE,Paint.Align.LEFT,true);txt(c,"+ ◎ "+wr.amount,r.right-dp(18),yy+dp(17),11,GOLD_LIGHT,Paint.Align.RIGHT,true);txtFit(c,wr.combo,tx,yy+dp(36),9,6,r.right-tx-dp(15),MUTED,Paint.Align.LEFT,false);
        }
        if(pokerWinners.isEmpty())txt(c,"Раздача завершена",r.centerX(),r.centerY(),11,WHITE,Paint.Align.CENTER,true);
        RectF b=new RectF(r.centerX()-dp(95),r.bottom-dp(58),r.centerX()+dp(95),r.bottom-dp(12));button(c,b,"НОВАЯ РАЗДАЧА","newPokerHand",true);
    }

    private void drawMiniCard(Canvas c,Card card,float cx,float bottom,boolean tiny){float w=tiny?dp(26):dp(36),h=tiny?dp(36):dp(49);RectF r=new RectF(cx-w/2,bottom-h,cx+w/2,bottom);round(c,r,5,Color.rgb(247,246,240));int col=(card.s==1||card.s==2)?Color.rgb(199,46,43):Color.rgb(21,26,25);txt(c,card.rank(),r.left+dp(4),r.top+dp(12),tiny?7.5f:9,col,Paint.Align.LEFT,true);txt(c,card.suit(),r.centerX(),r.centerY()+dp(7),tiny?13:17,col,Paint.Align.CENTER,false);}
    private void drawCardBack(Canvas c,float cx,float bottom,boolean tiny){float w=tiny?dp(26):dp(36),h=tiny?dp(36):dp(49);RectF r=new RectF(cx-w/2,bottom-h,cx+w/2,bottom);round(c,r,5,Color.rgb(218,222,218));RectF in=new RectF(r);in.inset(dp(3),dp(3));round(c,in,4,Color.rgb(16,64,82));for(float x=in.left-dp(20);x<in.right+dp(20);x+=dp(8)){p.setColor(Color.argb(90,87,144,163));p.setStrokeWidth(dp(2.5f));c.drawLine(x,in.bottom,x+dp(35),in.top,p);}}
    private void drawFullCard(Canvas c,Card card,float cx,float cy,float scale){float w=dp(48)*scale,h=dp(68)*scale;RectF r=new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2);round(c,r,8,Color.rgb(248,247,242));int col=(card.s==1||card.s==2)?Color.rgb(201,48,44):Color.rgb(20,24,23);txt(c,card.rank(),r.left+dp(6),r.top+dp(17),11*scale,col,Paint.Align.LEFT,true);txt(c,card.suit(),r.centerX(),r.centerY()+dp(10),25*scale,col,Paint.Align.CENTER,false);}

    private void animateBet(int idx){
        RectF t=tableRect();PointF s=seatPos(t,idx,landscape());float bx=s.x+(t.centerX()-s.x)*.34f,by=s.y+(t.centerY()-s.y)*.34f;ChipFly f=new ChipFly();f.x0=s.x;f.y0=s.y;f.x1=bx;f.y1=by;f.born=System.currentTimeMillis();f.life=340;chipFlys.add(f);invalidate();
    }

    private void animateCollectBets(){
        RectF t=tableRect();long now=System.currentTimeMillis();for(int i=0;i<pp.size();i++){Player q=pp.get(i);if(q.streetBet<=0)continue;PointF s=seatPos(t,i,landscape());float bx=s.x+(t.centerX()-s.x)*.34f,by=s.y+(t.centerY()-s.y)*.34f;for(int k=0;k<Math.min(4,1+q.streetBet/150);k++){ChipFly f=new ChipFly();f.x0=bx+k*dp(2);f.y0=by-k*dp(2);f.x1=t.centerX()+((i+k)%3-1)*dp(5);f.y1=t.centerY()-dp(55);f.born=now+k*45;f.life=420;chipFlys.add(f);}}
    }

    private void animatePotTo(int idx){
        RectF t=tableRect();PointF s=seatPos(t,idx,landscape());for(int k=0;k<12;k++){ChipFly f=new ChipFly();f.x0=t.centerX()+((k%4)-1.5f)*dp(8);f.y0=t.centerY()-dp(55)+(k%3)*dp(3);f.x1=s.x+(rng.nextFloat()-.5f)*dp(24);f.y1=s.y;f.born=System.currentTimeMillis()+k*30;f.life=520;chipFlys.add(f);}invalidate();
    }

    // ---------- Hand evaluator ----------
    private HandValue evaluate(List<Card> cards){
        if(cards.size()<5){int hi=0;for(Card c:cards)hi=Math.max(hi,c.r);return new HandValue(new int[]{0,hi},"Старшая карта · "+rankName(hi));}
        HandValue best=null;int n=cards.size();
        for(int a=0;a<n-4;a++)for(int b=a+1;b<n-3;b++)for(int d=b+1;d<n-2;d++)for(int e=d+1;e<n-1;e++)for(int f=e+1;f<n;f++){
            Card[] x={cards.get(a),cards.get(b),cards.get(d),cards.get(e),cards.get(f)};HandValue hv=eval5(x);if(best==null||hv.compareTo(best)>0)best=hv;
        }
        return best;
    }

    private HandValue eval5(Card[] a){
        int[] cnt=new int[15];boolean flush=true;int suit=a[0].s;ArrayList<Integer> rs=new ArrayList<>();
        for(Card c:a){cnt[c.r]++;if(c.s!=suit)flush=false;if(!rs.contains(c.r))rs.add(c.r);}Collections.sort(rs,Collections.reverseOrder());if(rs.contains(14))rs.add(1);
        int straight=0,run=0,prev=-99;for(int r:rs){if(prev==r+1)run++;else run=1;if(run>=5){straight=r+4;break;}prev=r;}
        if(flush&&straight>0)return new HandValue(new int[]{8,straight},"Стрит-флеш · "+rankName(straight));
        int four=0,three=0;ArrayList<Integer> pairs=new ArrayList<>();for(int r=14;r>=2;r--){if(cnt[r]==4)four=r;else if(cnt[r]==3&&three==0)three=r;else if(cnt[r]>=2)pairs.add(r);}
        if(four>0){int k=0;for(int r=14;r>=2;r--)if(r!=four&&cnt[r]>0){k=r;break;}return new HandValue(new int[]{7,four,k},"Каре · "+rankName(four));}
        if(three>0&&!pairs.isEmpty())return new HandValue(new int[]{6,three,pairs.get(0)},"Фулл-хаус · "+rankName(three));
        if(flush){int[] v=new int[6];v[0]=5;for(int i=0;i<5;i++)v[i+1]=rs.get(i);return new HandValue(v,"Флеш · "+rankName(rs.get(0)));}
        if(straight>0)return new HandValue(new int[]{4,straight},"Стрит · до "+rankName(straight));
        if(three>0){ArrayList<Integer> k=new ArrayList<>();for(int r=14;r>=2;r--)if(r!=three&&cnt[r]>0)k.add(r);return new HandValue(new int[]{3,three,k.get(0),k.get(1)},"Сет · "+rankName(three));}
        if(pairs.size()>=2){int p1=pairs.get(0),p2=pairs.get(1),k=0;for(int r=14;r>=2;r--)if(r!=p1&&r!=p2&&cnt[r]>0){k=r;break;}return new HandValue(new int[]{2,p1,p2,k},"Две пары · "+rankName(p1)+" / "+rankName(p2));}
        if(pairs.size()==1){int pr=pairs.get(0);ArrayList<Integer> k=new ArrayList<>();for(int r=14;r>=2;r--)if(r!=pr&&cnt[r]>0)k.add(r);return new HandValue(new int[]{1,pr,k.get(0),k.get(1),k.get(2)},"Пара · "+rankName(pr));}
        int[] v=new int[6];v[0]=0;int j=1;for(int r=14;r>=2;r--)if(cnt[r]>0)v[j++]=r;return new HandValue(v,"Старшая карта · "+rankName(v[1]));
    }

    private String rankName(int r){return r==14?"A":r==13?"K":r==12?"Q":r==11?"J":String.valueOf(r);}

    // ---------------- Blackjack ----------------
    private void enterBJ(){screen=Screen.BLACKJACK;newBJTable();invalidate();}

    private void newBJTable(){
        ArrayList<OpponentDef>a=randomOpponents(4);bjDealer=a.get(0);bjOthers.clear();for(int i=1;i<a.size();i++)bjOthers.add(a.get(i));
        bjRound=false;bjDone=false;dealerReveal=false;bjMessage="Сделайте ставку";bjHands.clear();dealerCards.clear();bjOtherHands.clear();bjNetWin=0;insuranceOffered=false;insuranceTaken=false;
    }

    private int bjValue(List<Card> cards){int sum=0,aces=0;for(Card c:cards){if(c.r==14){sum+=11;aces++;}else sum+=Math.min(10,c.r);}while(sum>21&&aces-->0)sum-=10;return sum;}
    private boolean bjNatural(BJHand h){return h.cards.size()==2&&bjValue(h.cards)==21;}

    private void dealBJ(){
        if(bjRound||chips<bjBet)return;chips-=bjBet;save();bjDeck=new Deck(6,rng);bjHands.clear();BJHand u=new BJHand(bjBet);bjHands.add(u);dealerCards.clear();bjOtherHands.clear();for(int i=0;i<bjOthers.size();i++)bjOtherHands.add(new ArrayList<Card>());
        long base=System.currentTimeMillis()+100;for(int r=0;r<2;r++){u.cards.add(bjDeck.draw());for(ArrayList<Card> h:bjOtherHands)h.add(bjDeck.draw());dealerCards.add(bjDeck.draw());}
        bjRound=true;bjDone=false;dealerReveal=false;insuranceOffered=dealerCards.get(0).r==14;insuranceTaken=false;insuranceBet=0;bjNetWin=0;bjMessage=insuranceOffered?"У дилера туз · доступна страховка":"Ваш ход";autoPlayBJOthers();animateBJDeal(base);
        if(bjNatural(u)&&!insuranceOffered)handler.postDelayed(new Runnable(){@Override public void run(){finishBJDealer();}},900);invalidate();
    }

    private void animateBJDeal(long base){
        RectF t=tableRect();boolean land=landscape();float dx=t.centerX(),dy=t.top+t.height()*.19f;float uy=t.bottom-(land?dp(190):dp(265));
        int seq=0;for(int r=0;r<2;r++){
            CardFly fu=new CardFly();fu.card=bjHands.get(0).cards.get(r);fu.x0=dx;fu.y0=dy;fu.x1=t.centerX()+(r==0?-dp(22):dp(22));fu.y1=uy;fu.scale=.9f;fu.born=base+seq++*110;fu.life=360;cardFlys.add(fu);
            CardFly fd=new CardFly();fd.card=dealerCards.get(r);fd.back=(r==1);fd.x0=dx;fd.y0=dy;fd.x1=t.centerX()+(r==0?-dp(21):dp(21));fd.y1=t.top+t.height()*.29f;fd.scale=.82f;fd.born=base+seq++*110;fd.life=360;cardFlys.add(fd);
        }
    }

    private void autoPlayBJOthers(){for(ArrayList<Card> h:bjOtherHands)while(bjValue(h)<16)h.add(bjDeck.draw());}

    private void bjAction(String a){
        if(!bjRound||bjDone)return;BJHand h=bjHands.get(0);
        if(a.equals("insurance")&&insuranceOffered&&!insuranceTaken){int cost=h.bet/2;if(chips>=cost){chips-=cost;insuranceTaken=true;insuranceBet=cost;save();bjMessage="Страховка принята · ваш ход";}invalidate();return;}
        if(a.equals("hit")){
            Card card=bjDeck.draw();h.cards.add(card);animateBJHit(card);int v=bjValue(h.cards);if(v>21){h.busted=true;h.stood=true;bjMessage="Перебор";handler.postDelayed(new Runnable(){@Override public void run(){finishBJDealer();}},450);}else if(v==21){h.stood=true;bjMessage="21";handler.postDelayed(new Runnable(){@Override public void run(){finishBJDealer();}},450);}else bjMessage="Ваш ход · "+v;
        } else if(a.equals("stand")){h.stood=true;bjMessage="Дилер играет";finishBJDealer();}
        invalidate();
    }

    private void animateBJHit(Card card){
        RectF t=tableRect();boolean land=landscape();float uy=t.bottom-(land?dp(190):dp(265));CardFly f=new CardFly();f.card=card;f.x0=t.centerX();f.y0=t.top+t.height()*.20f;f.x1=t.centerX()+dp((bjHands.get(0).cards.size()-2)*23);f.y1=uy;f.scale=.9f;f.born=System.currentTimeMillis();f.life=340;cardFlys.add(f);
    }

    private void finishBJDealer(){
        if(bjDone)return;dealerReveal=true;int dv=bjValue(dealerCards);while(dv<16){Card card=bjDeck.draw();dealerCards.add(card);dv=bjValue(dealerCards);}settleBJ();
    }

    private void settleBJ(){
        int dv=bjValue(dealerCards);boolean dealerBust=dv>21;boolean dealerBJ=dealerCards.size()==2&&dv==21;BJHand h=bjHands.get(0);int v=bjValue(h.cards);int ret=0;
        if(h.busted)h.result="Проигрыш";
        else if(dealerBJ&&!bjNatural(h))h.result="Дилер: blackjack";
        else if(bjNatural(h)&&!dealerBJ){ret=h.bet+(h.bet*3)/2;h.result="BLACKJACK";}
        else if(dealerBJ&&bjNatural(h)){ret=h.bet;h.result="Push";}
        else if(dealerBust||v>dv){ret=h.bet*2;h.result="Победа";}
        else if(v==dv){ret=h.bet;h.result="Push";}else h.result="Проигрыш";
        chips+=ret;bjNetWin=ret-h.bet;
        if(insuranceTaken&&dealerBJ){int insuranceReturn=insuranceBet*3;chips+=insuranceReturn;bjNetWin+=insuranceReturn-insuranceBet;}
        save();bjDone=true;bjMessage=dealerBust?"Дилер перебрал":dealerBJ?"Blackjack у дилера":"Дилер остановился на "+dv;if(bjNetWin>0)celebrate();invalidate();
    }

    private void drawBlackjack(Canvas c){
        topBar(c,bjMessage,"BLACKJACKPORTER");RectF t=tableRect();drawTable(c,t);boolean land=landscape();drawBack(c);
        float dealerY=t.top+(land?t.height()*.14f:t.height()*.12f);RectF da=new RectF(t.centerX()-dp(38),dealerY-dp(38),t.centerX()+dp(38),dealerY+dp(38));drawAvatar(c,bjDealer.res,da,11);outline(c,da,11,GOLD,2);
        txtFit(c,"ДИЛЕР · "+bjDealer.name,t.centerX(),da.bottom+dp(22),11,7,t.width()*.55f,WHITE,Paint.Align.CENTER,true);
        if(bjRound){float cx=t.centerX()-(dealerCards.size()-1)*dp(20);for(int i=0;i<dealerCards.size();i++){if(i==1&&!dealerReveal)drawCardBack(c,cx+i*dp(40),da.bottom+dp(84),false);else drawFullCard(c,dealerCards.get(i),cx+i*dp(40),da.bottom+dp(57),.78f);}if(dealerReveal)txt(c,""+bjValue(dealerCards),t.centerX()+dp(110),da.bottom+dp(64),11,GOLD_LIGHT,Paint.Align.LEFT,true);}
        drawBJOthers(c,t,land);drawBJUser(c,t,land);drawBJControls(c,t,land);if(bjDone)drawBJResult(c,t,land);
    }

    private void drawBJOthers(Canvas c,RectF t,boolean land){
        if(bjOthers.isEmpty())return;float y=t.top+t.height()*(land?.44f:.45f);for(int i=0;i<bjOthers.size();i++){float x=t.left+t.width()*(.25f+i*(.50f/Math.max(1,bjOthers.size()-1)));OpponentDef d=bjOthers.get(i);RectF a=new RectF(x-dp(29),y-dp(29),x+dp(29),y+dp(29));drawAvatar(c,d.res,a,9);outline(c,a,9,GOLD,1.3f);txtFit(c,d.name,x,y+dp(46),8.5f,6,dp(100),WHITE,Paint.Align.CENTER,true);if(bjRound&&i<bjOtherHands.size())txt(c,""+bjValue(bjOtherHands.get(i)),x,y+dp(62),7.5f,GOLD_LIGHT,Paint.Align.CENTER,true);}
    }

    private void drawBJUser(Canvas c,RectF t,boolean land){
        float y=t.bottom-(bjRound?(land?dp(190):dp(265)):(land?dp(132):dp(215)));
        if(!bjRound){
            txt(c,"Ставка",t.centerX()-dp(120),y,11,MUTED,Paint.Align.LEFT,false);RectF minus=new RectF(t.centerX()-dp(55),y-dp(34),t.centerX()-dp(10),y+dp(10));button(c,minus,"−","bjBetMinus",false);txt(c,"◎ "+bjBet,t.centerX()+dp(25),y,16,WHITE,Paint.Align.CENTER,true);RectF plus=new RectF(t.centerX()+dp(65),y-dp(34),t.centerX()+dp(110),y+dp(10));button(c,plus,"+","bjBetPlus",false);RectF deal=new RectF(t.centerX()-dp(85),y+dp(22),t.centerX()+dp(85),y+dp(72));button(c,deal,"РАЗДАТЬ","bjDeal",true);RectF nr=new RectF(t.centerX()-dp(85),y+dp(82),t.centerX()+dp(85),y+dp(127));button(c,nr,"НОВЫЙ СОСТАВ","bjRoster",false);return;
        }
        BJHand h=bjHands.get(0);float cx=t.centerX()-(h.cards.size()-1)*dp(21);for(int i=0;i<h.cards.size();i++)drawFullCard(c,h.cards.get(i),cx+i*dp(42),y,.88f);txt(c,"ВЫ · "+bjValue(h.cards)+" · ставка "+h.bet,t.centerX(),y+dp(59),10.5f,WHITE,Paint.Align.CENTER,true);if(!h.result.isEmpty())txt(c,h.result,t.centerX(),y+dp(78),9.5f,GOLD_LIGHT,Paint.Align.CENTER,true);
    }

    private void drawBJControls(Canvas c,RectF t,boolean land){
        if(!bjRound||bjDone)return;float safe=land?dp(74):dp(104),y=t.bottom-safe;int buttons=insuranceOffered&&!insuranceTaken?3:2;float w=Math.min(t.width()*.72f,land?dp(480):t.width()*.84f),gap=dp(8),bw=(w-gap*(buttons-1))/buttons,x=t.centerX()-w/2,bh=land?dp(48):dp(54);
        RectF b=new RectF(x,y-bh,x+bw,y);button(c,b,"HIT","bjHit",false);b.offset(bw+gap,0);button(c,b,"STAND","bjStand",true);
        if(buttons==3){b.offset(bw+gap,0);button(c,b,"INSURANCE","bjInsurance",false);}
    }

    private void drawBJResult(Canvas c,RectF t,boolean land){
        float w=Math.min(t.width()*.72f,dp(400)),h=land?dp(150):dp(190);RectF r=new RectF(t.centerX()-w/2,t.centerY()-h/2,t.centerX()+w/2,t.centerY()+h/2);round(c,r,18,Color.argb(248,2,22,17));outline(c,r,18,bjNetWin>0?GOLD:Color.rgb(80,100,92),2);
        RectF ar=new RectF(r.left+dp(18),r.top+dp(38),r.left+dp(70),r.top+dp(90));round(c,ar,10,Color.rgb(30,70,57));txt(c,"YOU",ar.centerX(),ar.centerY()+dp(6),14,WHITE,Paint.Align.CENTER,true);outline(c,ar,10,GOLD,1.2f);
        BJHand bh=bjHands.get(0);txt(c,bh.result,r.left+dp(84),r.top+dp(54),14,bjNetWin>0?GOLD_LIGHT:WHITE,Paint.Align.LEFT,true);txt(c,"Итог: "+bjValue(bh.cards),r.left+dp(84),r.top+dp(78),10,MUTED,Paint.Align.LEFT,false);txt(c,(bjNetWin>=0?"+ ":"− ")+"◎ "+Math.abs(bjNetWin),r.right-dp(18),r.top+dp(58),13,bjNetWin>0?GOLD_LIGHT:MUTED,Paint.Align.RIGHT,true);
        RectF n=new RectF(r.centerX()-dp(90),r.bottom-dp(58),r.centerX()+dp(90),r.bottom-dp(12));button(c,n,"НОВАЯ ИГРА","bjReset",true);
    }

    // ---------------- Effects / audio ----------------
    private void celebrate(){
        long now=System.currentTimeMillis();int[] colors={GOLD,Color.WHITE,Color.rgb(239,80,70),Color.rgb(92,185,255),Color.rgb(108,226,139)};
        for(int i=0;i<110;i++){Confetti q=new Confetti();q.x=rng.nextFloat()*getWidth();q.y=-rng.nextFloat()*getHeight()*.3f;q.vx=(rng.nextFloat()-.5f)*dp(2.7f);q.vy=dp(2.3f+rng.nextFloat()*4);q.rot=rng.nextFloat()*360;q.vr=(rng.nextFloat()-.5f)*12;q.color=colors[rng.nextInt(colors.length)];q.born=now+rng.nextInt(450);q.life=2200+rng.nextInt(1200);confetti.add(q);}
        for(int i=0;i<6;i++){Firework f=new Firework();f.x=getWidth()*(.15f+rng.nextFloat()*.7f);f.y=getHeight()*(.18f+rng.nextFloat()*.48f);f.max=dp(45+rng.nextInt(55));f.color=colors[rng.nextInt(colors.length)];f.born=now+180+i*210;f.life=800;fireworks.add(f);}
        if(soundOn)playWinMelody();invalidate();
    }

    private void playWinMelody(){
        final int[] notes={ToneGenerator.TONE_DTMF_1,ToneGenerator.TONE_DTMF_3,ToneGenerator.TONE_DTMF_6,ToneGenerator.TONE_DTMF_9,ToneGenerator.TONE_PROP_ACK};
        for(int i=0;i<notes.length;i++){final int n=notes[i];handler.postDelayed(new Runnable(){@Override public void run(){if(soundOn)tone.startTone(n,150);}},i*155L);}
    }

    private void drawEffects(Canvas c){
        long now=System.currentTimeMillis();boolean alive=false;
        Iterator<CardFly> cf=cardFlys.iterator();while(cf.hasNext()){
            CardFly f=cf.next();float u=(now-f.born)/(float)f.life;if(u<0){alive=true;continue;}if(u>=1){cf.remove();continue;}alive=true;float e=1-(1-u)*(1-u);float x=f.x0+(f.x1-f.x0)*e,y=f.y0+(f.y1-f.y0)*e;if(f.back)drawCardBack(c,x,y+dp(24)*f.scale,f.scale<.8f);else drawFullCard(c,f.card,x,y,f.scale);
        }
        Iterator<ChipFly> ci=chipFlys.iterator();while(ci.hasNext()){
            ChipFly f=ci.next();float u=(now-f.born)/(float)f.life;if(u<0){alive=true;continue;}if(u>=1){ci.remove();continue;}alive=true;float e=1-(1-u)*(1-u);float x=f.x0+(f.x1-f.x0)*e,y=f.y0+(f.y1-f.y0)*e;for(int k=0;k<3;k++){p.setColor(k==0?Color.rgb(170,39,34):k==1?GOLD:BLUE);c.drawOval(new RectF(x-dp(6)+k*dp(1.5f),y-dp(3)-k*dp(2.5f),x+dp(6)+k*dp(1.5f),y+dp(3)-k*dp(2.5f)),p);}
        }
        Iterator<Confetti> it=confetti.iterator();while(it.hasNext()){
            Confetti q=it.next();float u=(now-q.born)/(float)q.life;if(u<0){alive=true;continue;}if(u>=1){it.remove();continue;}alive=true;float x=q.x+q.vx*u*120,y=q.y+q.vy*u*105+dp(120)*u*u;c.save();c.rotate(q.rot+q.vr*u*30,x,y);p.setColor(q.color);c.drawRect(x-dp(3),y-dp(6),x+dp(3),y+dp(6),p);c.restore();
        }
        Iterator<Firework> fi=fireworks.iterator();while(fi.hasNext()){
            Firework f=fi.next();float u=(now-f.born)/(float)f.life;if(u<0){alive=true;continue;}if(u>=1){fi.remove();continue;}alive=true;p.setColor(f.color);p.setStrokeWidth(dp(2));for(int k=0;k<18;k++){double a=k*Math.PI*2/18;float r=f.max*Math.min(1,u*1.4f);float x1=f.x+(float)Math.cos(a)*r*.35f,y1=f.y+(float)Math.sin(a)*r*.35f,x2=f.x+(float)Math.cos(a)*r,y2=f.y+(float)Math.sin(a)*r;c.drawLine(x1,y1,x2,y2,p);}
        }
        if(alive)postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY();
        for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.r.contains(x,y)){handle(h.a);break;}}return true;
    }

    private void handle(String a){
        if(a.equals("sound")){soundOn=!soundOn;save();invalidate();return;}
        if(a.equals("back")){screen=Screen.MENU;invalidate();return;}
        if(a.equals("menuPoker")){screen=Screen.POKER;newPokerRoster();invalidate();return;}
        if(a.equals("menuBJ")){enterBJ();return;}
        if(a.equals("fold")||a.equals("checkcall")||a.equals("raise")||a.equals("allin")){pokerAction(a);return;}
        if(a.equals("raiseMinus")){raiseTarget=Math.max(currentBet+minRaise,raiseTarget-20);invalidate();return;}
        if(a.equals("raisePlus")){Player u=pp.get(0);raiseTarget=Math.min(u.streetBet+u.stack,raiseTarget+20);invalidate();return;}
        if(a.equals("newPokerHand")){startPokerHand();return;}
        if(a.equals("newPokerTable")){newPokerRoster();return;}
        if(a.equals("bjBetMinus")){bjBet=Math.max(20,bjBet-20);invalidate();return;}
        if(a.equals("bjBetPlus")){bjBet=Math.min(Math.max(20,chips),bjBet+20);invalidate();return;}
        if(a.equals("bjDeal")){dealBJ();return;}
        if(a.equals("bjRoster")){newBJTable();invalidate();return;}
        if(a.equals("bjHit")){bjAction("hit");return;}
        if(a.equals("bjStand")){bjAction("stand");return;}
        if(a.equals("bjInsurance")){bjAction("insurance");return;}
        if(a.equals("bjReset")){bjRound=false;bjDone=false;dealerReveal=false;bjMessage="Сделайте ставку";bjHands.clear();dealerCards.clear();bjNetWin=0;invalidate();}
    }
}
