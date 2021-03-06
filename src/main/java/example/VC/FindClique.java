package example.VC;

import example.data.FindCliquesData;
import example.utils.ReadFromText;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.GraphAlgorithm;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.pregel.ComputeFunction;
import org.apache.flink.graph.pregel.MessageCombiner;
import org.apache.flink.graph.pregel.MessageIterator;
import org.apache.flink.types.NullValue;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @Author: jaxon
 * @Description:
 * @Date: 2017/8/26
 * @Time: 下午2:53
 * @Project: GellyPractice
 */
public class FindClique<K,EV>  implements GraphAlgorithm<K,ArrayList<List<K>>,EV,DataSet<Vertex<K,ArrayList<List<K>>>>> {


    public  static Logger logger = Logger.getLogger("D");

    private  int maxIterations = 20;

    @Override
    public DataSet<Vertex<K, ArrayList<List<K>>>> run(Graph<K, ArrayList<List<K>>, EV> input) throws Exception {

        Graph<K,ArrayList<List<K>>,EV> initialGraph = input.mapVertices(new VertexInitializer<K>());
        DataSet<Vertex<K,ArrayList<List<K>>>> vertices = initialGraph.runVertexCentricIteration(new CliquesFindComputionFunction<K, EV>(),new CliquesMessageCombiner<K,EV>(),maxIterations).getVertices();
        return vertices;
    }


    public FindClique(int maxIterations) {

        this.maxIterations = maxIterations;
    }

    public static  final class VertexInitializer<K> implements MapFunction<Vertex<K,ArrayList<List<K>>>,ArrayList<List<K>>> {


        @Override
        public ArrayList<List<K>> map(Vertex<K, ArrayList<List<K>>> vertex) throws Exception {

            K vid = vertex.getId();

            ArrayList<List<K>> list = new ArrayList<List<K>>();

            ArrayList<K> sublist = new ArrayList<K>();

            sublist.add(vid);
            list.add(sublist);

            return list;
        }
    }

    public static final class  CliquesMessageCombiner<K,EV> extends MessageCombiner<K,ArrayList<List<K>>>{
        @Override
        public void combineMessages(MessageIterator<ArrayList<List<K>>> arrayLists) throws Exception {

            ArrayList<List<K>> msgs = new ArrayList<List<K>>();

            while(arrayLists.hasNext()){
                ArrayList<List<K>> l = arrayLists.next();
                msgs.addAll(l);
            }

            int n = msgs.size();

            for(int i = 0 ; i < n ; i++){
                List<K> l1 = msgs.get(i);
                if(l1 == null) continue;;
                for(int j = i+1 ; j < n ; j++){
                        List<K> l2 = msgs.get(j);
                        if(l2 == null) continue;
                        if(l1.contains(l2) && l2.contains(l1))
                            msgs.set(j,null);
                }
            }

            for(int i = 0 ; i < msgs.size();){
                if(msgs.get(i) == null){
                    msgs.remove(i);
                }else
                    i++;
            }

            sendCombinedMessage(msgs);
        }
    }

    public static final  class CliquesFindComputionFunction<K,EV> extends ComputeFunction<K,ArrayList<List<K>>,EV,ArrayList<List<K>>>{




        public CliquesFindComputionFunction() {

        }

        @Override
        public void compute(Vertex<K, ArrayList<List<K>>> vertex, MessageIterator<ArrayList<List<K>>> inMessages) throws Exception {

            if(getSuperstepNumber() == 1){

                ArrayList<List<K>> message = vertex.getValue();
                //System.out.println("SrcId=" + vertex.getId() + "  send first msg to all neighbors , msg = " + Arrays.deepToString(message.toArray()));
                logger.info("SrcId=" + vertex.getId() + "  send first msg to all neighbors , msg = " + Arrays.deepToString(message.toArray()));
                sendMessageToAllNeighbors(message);

            }else{

                ArrayList<List<K>> container = new ArrayList<List<K>>();

                ArrayList<List<K>> result = new ArrayList<List<K>>();

                List<K> neighbors = new ArrayList<K>();

                ArrayList<List<K>>  oldValue = vertex.getValue();


                Iterable<Edge<K,EV>> edges = getEdges();

                Iterator<Edge<K,EV>> eiter = edges.iterator();


                // get vertex's neighbors
                for(;eiter.hasNext();){
                    Edge e = eiter.next();

                    neighbors.add((K)e.f1);
                }
                //System.out.println("SuperStep=" + getSuperstepNumber() + " SrcId= " + vertex.getId() + "neighbors= " + neighbors.toString());

                logger.info("SuperStep=" + getSuperstepNumber() + " SrcId= " + vertex.getId() + "neighbors= " + neighbors.toString());
                //put previous superstep messages to container
                for(;inMessages.hasNext();){

                    List<List<K>> l = inMessages.next();

                    Iterator<List<K>> iter2 = l.iterator();
                    for(;iter2.hasNext();){
                        List<K> subl = iter2.next();
                        if(subl != null){
                            container.add(subl);
                        }
                    }
                }



                //delete duplicated list in container

                for(int i = 0; i < container.size(); i++){

                    List<K> l1 = container.get(i);

                    if(l1 == null) continue;

                    for(int j = i + 1; j < container.size(); j++){

                        List<K> l2 = container.get(j);

                        if(l2 == null) continue;

                        if(l1.containsAll(l2) && l2.containsAll(l1))
                            container.set(j,null);
                    }
                }




                //System.out.println("hello: superStep == " + getSuperstepNumber() + " vertexId = " + vertex.getId() + " container = " + Arrays.deepToString(container.toArray()));

                int t = 0;
                while(t < container.size()){
                    if(container.get(t) == null){
                        container.remove(t);
                    }else
                        t++;
                }
                logger.info("hello: superStep == " + getSuperstepNumber() + " vertexId = " + vertex.getId() + " container = " + Arrays.deepToString(container.toArray()));;
                //judge whether current vertex can add to the cliques result
                Iterator<List<K>> iter = container.iterator();

                for(;iter.hasNext();){
                    List<K> list = iter.next();

                    if(list.contains(vertex.getId()))
                        continue;
                   if(neighbors.containsAll(list)){
                        List<K> newlist = new ArrayList<K>(list);
                        newlist.add(vertex.getId());
                        result.add(newlist);
                   }

                }

                //filter the list which size less than max clique's size
                Iterator<List<K>> resultIter = result.iterator();
                int maxSize = 0;
                for(;resultIter.hasNext();){
                    List<K> l = resultIter.next();
                    int s = l.size();
                    if(s > maxSize) maxSize = s;
                }
//                resultIter = result.iterator();
//                for(;resultIter.hasNext();){
//                    List<K> l = resultIter.next();
//                    if(l.size() < maxSize) result.remove(l);
//                }

                int size = result.size();
                for(int i = 0 ; i < size ; ){
                    List<K> l = result.get(i);
                    if(l.size() < maxSize){
                        result.remove(i);
                    }else
                        i++;
                }


//                System.out.println("superStep = " + getSuperstepNumber()+ "SrcId = " + vertex.getId() + "vertex value= "
//                        + Arrays.deepToString(vertex.getValue().toArray()) + "   Nxtmsg=" + Arrays.deepToString(result.toArray()));

                // the result is equal to previous step, so don't need to send duplicated messages. and if result == null ,don't need to send msg
                // when vertex v's neighbors not contains all cliques v received, the result may be empty
                boolean flag = true;// init assume equal
                if(oldValue.size() == result.size()){

                    for(int p = 0 ; p < oldValue.size(); p ++){

                        List<K> l1 = oldValue.get(p);
                        List<K> l2 = result.get(p);
                        int m=p ,n = p;
                        for(; m < result.size() ; m ++){
                            List<K> tmp = result.get(m);
                            if(l1.containsAll(tmp) && tmp.containsAll(l1)){
                                break;
                            }
                        }

                        for(; n < oldValue.size() ; n ++ ){
                            List<K> tmp2 = oldValue.get(n);
                            if(l2.containsAll(tmp2) && tmp2.containsAll(l2)){
                                break;
                            }
                        }
                        if(m == result.size() || n == result.size()){ // not match
                            flag = false; //not equal
                            break;
                        }
                    }
                }else
                    flag = false;

                if(result.isEmpty())
                    flag = true;


                if(flag){

                    //System.out.println("SuperStep = " + getSuperstepNumber() +"  SrcId="+ vertex.getId() +" equal to previous value");
                    logger.info("SuperStep = " + getSuperstepNumber() +"  SrcId="+ vertex.getId() +" equal to previous value" + Arrays.deepToString(result.toArray()));
                    return;
                }

                setNewVertexValue(result);

                for(int j = 0; j < neighbors.size(); j ++){
                    sendMessageTo(neighbors.get(j),result);
                }
            }

        }
    }


    public static final  class VertexGenerate<K> implements MapFunction<Long,ArrayList<List<K>>>{

        @Override
        public ArrayList<List<K>> map(Long value) throws Exception {

            return new ArrayList<List<K>>();
        }
    }

    public static void main(String[] args) throws Exception{

        //PropertyConfigurator.configure("/Users/jaxon/code2/GellyPractice/src/main/resources/log4j.properties");


        BasicConfigurator.configure();
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        //env.getConfig().enableForceKryo();
//        Graph<Long,ArrayList<List<Long>>,NullValue> input = Graph.fromDataSet(FindCliquesData.getDefaultVerticsSet(env),FindCliquesData.getDefaultEdgeDataSet(env),env)
//                .getUndirected();

        //DataSet<Vertex<Long,ArrayList<List<Long>>>> res = new FindClique<Long,NullValue>().run(input);



        Graph<Long,ArrayList<List<Long>>,NullValue> input  = Graph.fromDataSet(ReadFromText.getEdgesFromText(args[0],env),new VertexGenerate<Long>(),env).getUndirected();




        DataSet<Vertex<Long,ArrayList<List<Long>>>> res = new FindClique<Long,NullValue>(Integer.valueOf(args[2])).run(input);


        res.writeAsCsv(args[1],"\n","\t");

        env.execute();

        //res.print();
    }

}
