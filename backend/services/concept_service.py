"""Concept service for managing the knowledge graph (DAG)."""
from typing import List, Dict, Set, Optional
from backend.models.learning import Concept


class ConceptGraph:
    """Manages the concept dependency graph (DAG)."""
    
    def __init__(self):
        self.concepts: Dict[str, Concept] = {}
        self._adjacency: Dict[str, Set[str]] = {}  # concept_id -> set of prerequisite IDs
    
    def add_concept(self, concept: Concept) -> None:
        """
        Add a concept to the graph.
        
        Args:
            concept: The concept to add
            
        Raises:
            ValueError: If adding this concept would create a cycle
        """
        # Check for cycles before adding
        if self._would_create_cycle(concept.id, concept.prerequisites):
            raise ValueError(f"Adding concept '{concept.id}' would create a cycle in the dependency graph")
        
        self.concepts[concept.id] = concept
        self._adjacency[concept.id] = set(concept.prerequisites)
    
    def get_concept(self, concept_id: str) -> Optional[Concept]:
        """Get a concept by ID."""
        return self.concepts.get(concept_id)
    
    def get_all_concepts(self) -> List[Concept]:
        """Get all concepts."""
        return list(self.concepts.values())
    
    def get_prerequisites(self, concept_id: str) -> List[str]:
        """Get direct prerequisites for a concept."""
        return list(self._adjacency.get(concept_id, set()))
    
    def get_all_prerequisites(self, concept_id: str) -> Set[str]:
        """
        Get all prerequisites (transitive closure) for a concept.
        
        Returns:
            Set of all prerequisite concept IDs
        """
        visited = set()
        to_visit = [concept_id]
        
        while to_visit:
            current = to_visit.pop()
            if current in visited or current not in self._adjacency:
                continue
            
            visited.add(current)
            prereqs = self._adjacency[current]
            to_visit.extend(prereqs)
        
        visited.discard(concept_id)  # Remove the concept itself
        return visited
    
    def get_ready_concepts(self, mastered_concepts: Set[str]) -> List[str]:
        """
        Get concepts that are ready to learn (all prerequisites mastered).
        
        Args:
            mastered_concepts: Set of concept IDs that are already mastered
            
        Returns:
            List of concept IDs that are ready to learn
        """
        ready = []
        
        for concept_id, prereqs in self._adjacency.items():
            # Skip if already mastered
            if concept_id in mastered_concepts:
                continue
            
            # Check if all prerequisites are mastered
            if prereqs.issubset(mastered_concepts):
                ready.append(concept_id)
        
        return ready
    
    def _would_create_cycle(self, concept_id: str, prerequisites: List[str]) -> bool:
        """
        Check if adding a concept with given prerequisites would create a cycle.
        
        Uses DFS to detect cycles.
        """
        # Temporarily add the concept
        temp_adj = self._adjacency.copy()
        temp_adj[concept_id] = set(prerequisites)
        
        # Check for cycles using DFS
        visited = set()
        rec_stack = set()
        
        def has_cycle(node: str) -> bool:
            visited.add(node)
            rec_stack.add(node)
            
            for neighbor in temp_adj.get(node, set()):
                if neighbor not in visited:
                    if has_cycle(neighbor):
                        return True
                elif neighbor in rec_stack:
                    return True
            
            rec_stack.remove(node)
            return False
        
        # Check from the new concept
        return has_cycle(concept_id)
    
    def validate_graph(self) -> bool:
        """
        Validate the entire graph for cycles and orphaned prerequisites.
        
        Returns:
            True if graph is valid
            
        Raises:
            ValueError: If graph is invalid
        """
        # Check for cycles
        visited = set()
        rec_stack = set()
        
        def has_cycle(node: str) -> bool:
            visited.add(node)
            rec_stack.add(node)
            
            for neighbor in self._adjacency.get(node, set()):
                if neighbor not in visited:
                    if has_cycle(neighbor):
                        return True
                elif neighbor in rec_stack:
                    return True
            
            rec_stack.remove(node)
            return False
        
        for concept_id in self.concepts:
            if concept_id not in visited:
                if has_cycle(concept_id):
                    raise ValueError(f"Cycle detected in concept graph involving '{concept_id}'")
        
        # Check for orphaned prerequisites
        for concept_id, prereqs in self._adjacency.items():
            for prereq in prereqs:
                if prereq not in self.concepts:
                    raise ValueError(f"Concept '{concept_id}' has unknown prerequisite '{prereq}'")
        
        return True


# Initialize with core financial concepts
def create_default_concept_graph() -> ConceptGraph:
    """Create the default concept graph with core financial literacy concepts."""
    graph = ConceptGraph()
    
    concepts = [
        # Level 1: Foundations (no prerequisites)
        Concept(
            id="money_basics",
            name="Money Basics",
            description="Understanding what money is and its role in daily life",
            prerequisites=[],
            difficulty=1,
            estimated_time_minutes=5
        ),
        Concept(
            id="income_basics",
            name="Income Basics",
            description="Understanding different sources of income",
            prerequisites=[],
            difficulty=1,
            estimated_time_minutes=5
        ),
        
        # Level 2: Basic skills
        Concept(
            id="expense_tracking",
            name="Expense Tracking",
            description="How to track and categorize your spending",
            prerequisites=["money_basics"],
            difficulty=2,
            estimated_time_minutes=8
        ),
        Concept(
            id="budgeting_basics",
            name="Budgeting Basics",
            description="Creating and maintaining a personal budget",
            prerequisites=["income_basics", "expense_tracking"],
            difficulty=2,
            estimated_time_minutes=10
        ),
        
        # Level 3: Intermediate concepts
        Concept(
            id="emergency_fund",
            name="Emergency Fund",
            description="Building and maintaining an emergency savings fund",
            prerequisites=["budgeting_basics"],
            difficulty=3,
            estimated_time_minutes=10
        ),
        Concept(
            id="debt_management",
            name="Debt Management",
            description="Understanding and managing different types of debt",
            prerequisites=["budgeting_basics"],
            difficulty=3,
            estimated_time_minutes=12
        ),
        Concept(
            id="saving_strategies",
            name="Saving Strategies",
            description="Effective strategies for building savings",
            prerequisites=["budgeting_basics"],
            difficulty=3,
            estimated_time_minutes=10
        ),
        
        # Level 4: Advanced concepts
        Concept(
            id="investment_basics",
            name="Investment Basics",
            description="Introduction to investing and growing wealth",
            prerequisites=["emergency_fund", "saving_strategies"],
            difficulty=4,
            estimated_time_minutes=15
        ),
        Concept(
            id="credit_score",
            name="Credit Score",
            description="Understanding credit scores and how to improve them",
            prerequisites=["debt_management"],
            difficulty=4,
            estimated_time_minutes=12
        ),
        Concept(
            id="financial_goals",
            name="Financial Goal Setting",
            description="Setting and achieving short and long-term financial goals",
            prerequisites=["budgeting_basics", "saving_strategies"],
            difficulty=4,
            estimated_time_minutes=12
        ),
    ]
    
    for concept in concepts:
        graph.add_concept(concept)
    
    graph.validate_graph()
    return graph


# Singleton instance
_concept_graph: Optional[ConceptGraph] = None

def get_concept_graph() -> ConceptGraph:
    """Get or create the concept graph singleton."""
    global _concept_graph
    if _concept_graph is None:
        _concept_graph = create_default_concept_graph()
    return _concept_graph
