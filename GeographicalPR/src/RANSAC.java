package app;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.util.*;
import java.io.*;
import java.lang.IllegalArgumentException;

import javax.swing.JFrame;

public class RANSAC {
    private ArrayList<Point> data = new ArrayList<Point>();
    private ArrayList<Boolean> correctSet = new ArrayList<Boolean>();
    private final int maxIter        = 200000;
    private final int threshold      = 8;
    private final int sufficientSize = 25; // Integer.MAX_VALUE; //(int) Double.POSITIVE_INFINITY;
    private final int width = 1000;
    private final int height = 1000;
    private final int pointSize = 4;

    /**
        @filePath Path to file containing points      
    */
    public RANSAC(String filePath) {
        Scanner sc = null;

        try {
            sc = new Scanner(new File(filePath));
        } catch(FileNotFoundException e) { }

        if(sc == null) {
            System.out.println("Something went wrong!");
        } else {
            while(sc.hasNext()){
                String[] points = sc.next().split(",");
                int x = Integer.parseInt(points[0]);
                int y = Integer.parseInt(points[1]);
                this.data.add(new Point(x, y));
            }
        }
    }

    /**
        @args[0] Path to file containing points      
    */
    public static void main(String[] args) throws IllegalArgumentException { 
        // if(args.length == 0){
        //     throw new IllegalArgumentException();
        // }
        String arg0 = "points.ht.data";
        new RANSAC(arg0).showCanvas();
        //new RANSAC(args[0]).showCanvas();
    }
    
    /**
     * Displays the data points and the calculated circles on a canvas
     */
    public void showCanvas(){
        final ArrayList<RANSACResult> r = this.execute();
  //      final Circle circle; // = r.getCircle();
  //      final ArrayList<Point> consensusSet; // = r.getConsensusSet();
        JFrame frame = new JFrame();
        final int width = this.width;
        final int height = this.height;
        final int pointSize = this.pointSize;
        final double offsetWidth = width / 2.0;
        final double offsetHeight = height / 2.0;
        final ArrayList<Point> data = this.data;

        frame.add(new Canvas(){

            private static final long serialVersionUID = 1L;

            @Override
            public void paint(Graphics g){
                double offsetWidth = this.getWidth() / 2.0;
                double offsetHeight = this.getHeight() / 2.0;
                g.translate((int) Math.round(offsetWidth), (int) Math.round(offsetHeight));
                for(Point point : data){
                    g.drawOval(
                        (int) (point.getX() + pointSize / 2.0 + 0.5),
                        (int) (point.getY() + pointSize / 2.0 + 0.5), 
                        pointSize, 
                        pointSize
                    );
                }
                
                for (RANSACResult oneR : r) {
                final Circle circle = oneR.getCircle();
                final ArrayList<Point> consensusSet = oneR.getConsensusSet();

                g.setColor(Color.RED);
                g.drawOval(
                    (int) (circle.getX() - circle.getRadius() + 0.5),
                    (int) (circle.getY() - circle.getRadius() + 0.5),
                    (int) (2 * circle.getRadius()), 
                    (int) (2 * circle.getRadius())
                );


                g.setColor(Color.GREEN);
                for(Point point : consensusSet) {
                    g.fillOval(
                        (int) (point.getX() + pointSize / 2.0 + 0.5),
                        (int) (point.getY() + pointSize / 2.0 + 0.5), 
                        pointSize, 
                        pointSize
                    );
                }


                double highestRadius = -1;
                double smallestRadius = Double.POSITIVE_INFINITY;
                for(Point point : consensusSet){
                    double distance = Math.sqrt(
                        Math.pow(point.getX() - circle.getX(), 2) + 
                        Math.pow(point.getY() - circle.getY(), 2)
                    );

                    if(distance > highestRadius) {
                        highestRadius = distance;
                    }

                    if(distance < smallestRadius) {
                        smallestRadius = distance;
                    }
                }

                g.setColor(Color.BLUE);
                g.drawOval(
                    (int) (circle.getX() - highestRadius + 0.5),
                    (int) (circle.getY() - highestRadius + 0.5),
                    (int) (2 * highestRadius), 
                    (int) (2 * highestRadius)
                );

                g.setColor(Color.ORANGE);
                g.drawOval(
                    (int) (circle.getX() - smallestRadius + 0.5),
                    (int) (circle.getY() - smallestRadius + 0.5),
                    (int) (2 * smallestRadius), 
                    (int) (2 * smallestRadius)
                );

                System.out.println("smallestRadius=" + smallestRadius);
                System.out.println("highestRadius=" + highestRadius);
        }
        }

        });
        frame.setSize(width, height);
        frame.setVisible(true);
    }
    
    /**
     * Runs the RANSAC algorithm
     * @return a RANSACResult, which contains the circle and its corresponding consensus set
     * @throws IllegalArgumentException
     */
    public ArrayList<RANSACResult> execute() throws IllegalArgumentException {
        ArrayList<RANSACResult> allFinds = new ArrayList<RANSACResult>();

        while ( this.data.size() > 0  && allFinds.size() < 15 ) {
            System.out.println("Number of data points = " + this.data.size());
        // if(this.data.size() == 0){
        //     throw new IllegalArgumentException("File containing points is empty");
        // }

        ArrayList<Integer> maybeInliers   = new ArrayList<Integer>();
        ArrayList<Point> bestConsensusSet = new ArrayList<Point>();;
        ArrayList<Point> consensusSet     = null;
        Circle bestCircle                 = null;
        Circle maybeCircle                = null;
        int bestIter = -1;

        double w = .25; // about 1/4 of the points are inliers 
        double P = .999; // nice to be about 99% sure 
        int n = 3; //  3 point (circle) model
        // log(1 - .99)/log(1 - .25^3)
        int computedIterations = (int)(Math.log1p(-P)/Math.log1p(-Math.pow(w, (double)n)));
        //computedIterations = 200000;
        System.out.println("computed Iterations = " + computedIterations);

        // while iterations < k
        anIteration:
        for (int i = 0; i < Math.min(computedIterations, this.maxIter); i++) {
            // maybe_inliers := n randomly selected values from data
            maybeInliers = this.getNPoints();
            consensusSet = new ArrayList<Point>();
            for (int index : maybeInliers) {
                consensusSet.add(this.data.get(index));
            }

            // maybe_model := model parameters fitted to maybe_inliers
            maybeCircle = this.getCircle(maybeInliers);

            // see if the center of this new circle is too close to previous circles
            for (int searchCircles=0; searchCircles<allFinds.size(); searchCircles++) {
                if ( Math.abs(allFinds.get(searchCircles).getCircle().getX() - maybeCircle.getX()) < 15
                  &&
                     Math.abs(allFinds.get(searchCircles).getCircle().getY() - maybeCircle.getY()) < 15) {
                    continue anIteration; // too close - bailout
                }
            }

            for (int j = 0; j < this.data.size(); j++) {
                if(maybeInliers.contains(j)){ continue; }

                Point point = this.data.get(j);
                double offset = this.getOffset(point, maybeCircle);
                
                // If this point is close enough to the circle, add it to the set
                if(offset < this.threshold){
                    // consensus_set := maybe_inliers
                    consensusSet.add(point);
                    
                    //if(consensusSet.size() > this.sufficientSize) { break; }
                }
            }
            //Keep track of the best model so far
            if(consensusSet.size() > bestConsensusSet.size()){
                bestConsensusSet = consensusSet;
                bestCircle = maybeCircle;
                bestIter = i;
            }

            // if(consensusSet.size() > Math.max(this.sufficientSize, maybeCircle.getRadius()/3.)) {
            //     System.out.println("found sufficient "  + consensusSet.size() + " " + this.sufficientSize);
            //     allFinds.add(new RANSACResult(consensusSet, maybeCircle));
            //     // this.data.add(new Point(x, y));
            //     // // loop all points in consensusSet and remove them for data
            //     // for (int j = 0; j < consensusSet.size(); j++) {
            //     //     this.data.remove(consensusSet.get(j));
            //     // }
            //     break;
            // }

            // if(consensusSet.size() > this.sufficientSize) { break; }
        }

        System.out.println("size of consensus set = " + bestConsensusSet.size() + ", radius = " + bestCircle.getRadius() + ", number of iterations = " + bestIter);
        // found the best; if it's not good enough then quit
        if(bestConsensusSet.size() < Math.max(this.sufficientSize, (int)(2.*Math.PI*bestCircle.getRadius()/13.))) { break;}
        // need someway to determine more even distribution of points in a circle if that's
        // waht's wanted.  This is still finding multiple circles fitting the same points when the raii are large.
        // Sort of like a large radius can fit a fairly flat line.  That might not be the reason to use a circle model.

         
        // // loop all points in consensusSet and remove them for data
        // for (int j = 0; j < bestConsensusSet.size(); j++) {
        //     this.data.remove(bestConsensusSet.get(j));
        // }

        // return new RANSACResult(bestConsensusSet, bestCircle);
        allFinds.add(new RANSACResult(bestConsensusSet, bestCircle));
        }
//allFinds.clear();
        return allFinds;
    }
    
    // Calculate the distance between a point and the center of a circle
    private double getOffset(Point point, Circle circle) {
        double x1 = point.getX();
        double y1 = point.getY();
        double x2 = circle.getX();
        double y2 = circle.getY();
        
        //Pythagorean theorem
        double hyp = Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
        return Math.abs(circle.getRadius() - hyp);
    }

    //Find the circle that passes through the three given points 
    // http://2000clicks.com/mathhelp/GeometryConicSectionCircleEquationGivenThreePoints.aspx
    private Circle getCircle(ArrayList<Integer> workingIndexes){
        ArrayList<Point> workingPoints = new ArrayList<Point>();

        for(int index : workingIndexes) {
            workingPoints.add(this.data.get(index));
        }

        double a = workingPoints.get(0).getX();
        double b = workingPoints.get(0).getY();

        double c = workingPoints.get(1).getX();
        double d = workingPoints.get(1).getY();

        double e = workingPoints.get(2).getX();
        double f = workingPoints.get(2).getY();
        
        double k = 0.5 * ((a * a + b * b) * (e - c) + (c * c + d * d) * (a - e) + (e * e + f * f) * (c - a)) / (b * (e - c) + d * (a - e) + f * (c - a));
        double h = 0.5 * ((a * a + b * b) * (f - d) + (c * c + d * d) * (b - f) + (e * e + f * f) * (d - b)) / (a * (f - d) + c * (b - f) + e * (d - b)); 
        double r = Math.sqrt(Math.pow(a - h, 2) + Math.pow(b - k, 2));

        return new Circle(h, k, r);
    }
    
    // Choose n data points at random
    private ArrayList<Integer> getNPoints(){
        ArrayList<Integer> collectedNumbers = new ArrayList<Integer>();
        ArrayList<Point> result             = new ArrayList<Point>();
        Random random                       = new Random();

        for (int n = 0; n < 3; n++) {
            while(true){
                int possibleNumber = random.nextInt(this.data.size());
                if(!collectedNumbers.contains(possibleNumber)){
                    collectedNumbers.add(possibleNumber);
                    break;
                }
            }
        }

        return collectedNumbers;
    }
    
    // Representation of the circle model with corresponding consensus set
    public class RANSACResult {
      private Circle circle;
      private ArrayList<Point> consensusSet;

      public RANSACResult(ArrayList<Point> consensusSet, Circle circle) {
        this.consensusSet = consensusSet;
        this.circle = circle;
      }

      public Circle getCircle() {
        return this.circle;
      }

      public ArrayList<Point> getConsensusSet(){
        return this.consensusSet;
      }
    }

    public class Circle {
        private double x;
        private double y;
        private double radius;

        public Circle(double x, double y, double radius){
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        public double getX(){
            return x;
        }

        public double getY(){
            return y;
        }

        public double getRadius(){
            return radius;
        }

        @Override
        public String toString(){
            return "X : " + x + ", Y: " + y + ", Radius: " + radius;
        }
    }
}
/*
https://en.wikipedia.org/wiki/Random_sample_consensus

Given:
    data – A set of observations.
    model – A model to explain observed data points.
    n – Minimum number of data points required to estimate model parameters.
    k – Maximum number of iterations allowed in the algorithm.
    t – Threshold value to determine data points that are fit well by model.
    d – Number of close data points required to assert that a model fits well to data.

Return:
    bestFit – model parameters which best fit the data (or nul if no good model is found)

iterations = 0
bestFit = nul
bestErr = something really large

while iterations < k do
    maybeInliers := n randomly selected values from data
    maybeModel := model parameters fitted to maybeInliers
    alsoInliers := empty set
    for every point in data not in maybeInliers do
        if point fits maybeModel with an error smaller than t
             add point to alsoInliers
    end for
    if the number of elements in alsoInliers is > d then
        // This implies that we may have found a good model
        // now test how good it is.
        betterModel := model parameters fitted to all points in maybeInliers and alsoInliers
        thisErr := a measure of how well betterModel fits these points
        if thisErr < bestErr then
            bestFit := betterModel
            bestErr := thisErr
        end if
    end if
    increment iterations
end while

return bestFit

=======================

http://w3.mi.parisdescartes.fr/~lomn/Cours/CV/SeqVideo/Material/RANSAC-tutorial.pdf

how to compute iteration estimate

probability of an inlier w = number of inliers/total number

n is number of parameters in model, e.g. a line is 2 points, a circle is 3 points, etc.

probability of selecting a model with all n inliers sample subset w^n

probability of selecting a model with outliers 1 - w^n

probability of choosing a model with outliers in all k iterations (fail, unsuccessful run) (1 - w^n)^k

probability of a successful run P = 1 - (1 - w^n)^k

P - 1 = - (1 - w^n)^k
1 - P = (1 - w^n)^k
log (1 - P) = k log(1 - w^n)
k = log(1 - P)/log(1 - w^n)

Say there are 50 points and 30 are valid inliers ; w = 30/50 = .6
Say the model is a straight line with 2 parameters n = 2
.6^2 = .36
Pick a P that is satisfying say .99
k = log(1 - .99)/log(1 - .36) = 11 iterations
k = log(1 - .999)/log(1 - .01) = 687 iterations another example

==

Also d can be used to terminate iterations but if too small may find the wrong solution and if too big may not find any solution.
*/

// about 1/4 of the points are inliers w = .25
// nice to be about 99% sure P = .99
// n = 3 point (circle) model
// log(1 - .99)/log(1 - .25^2)
